package com.exec.core.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableTransactionManagement
@EnableConfigurationProperties(DatabaseProperties.class)
@EnableJpaRepositories(
        basePackages = "com.exec.core.repository",
        entityManagerFactoryRef = "entityManagerFactory",
        transactionManagerRef = "transactionManager"
)
public class DatabaseConfig {

    private final DatabaseProperties databaseProperties;

    @Bean(name = "writeDataSource")
    public DataSource writeDataSource() {
        DatabaseProperties.WriteDataSource writeProps = databaseProperties.getWrite();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(writeProps.getUrl());
        config.setUsername(writeProps.getUsername());
        config.setPassword(writeProps.getPassword());
        config.setDriverClassName(writeProps.getDriverClassName());

        // Hikari 설정
        DatabaseProperties.HikariProperties hikari = writeProps.getHikari();
        config.setMaximumPoolSize(hikari.getMaximumPoolSize());
        config.setMinimumIdle(hikari.getMinimumIdle());
        config.setConnectionTimeout(hikari.getConnectionTimeout());
        config.setValidationTimeout(hikari.getValidationTimeout());
        config.setIdleTimeout(hikari.getIdleTimeout());
        config.setMaxLifetime(hikari.getMaxLifetime());
        config.setConnectionTestQuery(hikari.getConnectionTestQuery());
        config.setAutoCommit(hikari.isAutoCommit());

        // 커넥션 풀 이름 설정
        config.setPoolName("WriteHikariPool");

        log.info("Write DataSource configured: {}", writeProps.getUrl());
        return new HikariDataSource(config);
    }

    @Bean(name = "readDataSource")
    public DataSource readDataSource() {
        DatabaseProperties.ReadDataSource readProps = databaseProperties.getRead();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(readProps.getUrl());
        config.setUsername(readProps.getUsername());
        config.setPassword(readProps.getPassword());
        config.setDriverClassName(readProps.getDriverClassName());

        // Hikari 설정
        DatabaseProperties.HikariProperties hikari = readProps.getHikari();
        config.setMaximumPoolSize(hikari.getMaximumPoolSize());
        config.setMinimumIdle(hikari.getMinimumIdle());
        config.setConnectionTimeout(hikari.getConnectionTimeout());
        config.setValidationTimeout(hikari.getValidationTimeout());
        config.setIdleTimeout(hikari.getIdleTimeout());
        config.setMaxLifetime(hikari.getMaxLifetime());
        config.setConnectionTestQuery(hikari.getConnectionTestQuery());
        config.setAutoCommit(hikari.isAutoCommit());

        // 커넥션 풀 이름 설정
        config.setPoolName("ReadHikariPool");

        log.info("Read DataSource configured: {}", readProps.getUrl());
        return new HikariDataSource(config);
    }

    @Bean(name = "routingDataSource")
    public DataSource routingDataSource() {
        ReplicationRoutingDataSource routingDataSource = new ReplicationRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("write", writeDataSource());
        dataSourceMap.put("read", readDataSource());

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(writeDataSource());

        log.info("Routing DataSource configured with read/write splitting");
        return routingDataSource;
    }

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource() {
        // LazyConnectionDataSourceProxy 를 사용하여 실제 연결이 필요한 시점까지 지연
        return new LazyConnectionDataSourceProxy(routingDataSource());
    }

    @Primary
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan("com.exec.core.domain");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(hibernateProperties());

        return em;
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }

    private Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "validate");
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.setProperty("hibernate.show_sql", "false");
        properties.setProperty("hibernate.format_sql", "false");
        properties.setProperty("hibernate.use_sql_comments", "false");
        properties.setProperty("hibernate.jdbc.batch_size", "20");
        properties.setProperty("hibernate.jdbc.fetch_size", "100");
        properties.setProperty("hibernate.order_inserts", "true");
        properties.setProperty("hibernate.order_updates", "true");
        properties.setProperty("hibernate.jdbc.batch_versioned_data", "true");

        return properties;
    }
}