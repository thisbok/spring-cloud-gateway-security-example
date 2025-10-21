package com.exec.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "database")
public class DatabaseProperties {

    private ReadDataSource read = new ReadDataSource();
    private WriteDataSource write = new WriteDataSource();

    @Getter
    @Setter
    public static class ReadDataSource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private HikariProperties hikari = new HikariProperties();
    }

    @Getter
    @Setter
    public static class WriteDataSource {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        private HikariProperties hikari = new HikariProperties();
    }

    @Getter
    @Setter
    public static class HikariProperties {
        private int maximumPoolSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeout = 30000;
        private long validationTimeout = 5000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
        private String connectionTestQuery = "SELECT 1";
        private boolean autoCommit = true;
    }
}