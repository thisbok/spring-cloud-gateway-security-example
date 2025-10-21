package com.exec.services.apikey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@EnableJpaAuditing
@ComponentScan({
        "com.exec.services.apikey",     // 현재 서비스 패키지
        "com.exec.common",              // 공통 컴포넌트 (CryptoUtil 등)
        "com.exec.core"                 // 코어 모듈 (DatabaseConfig, Repository 등)
})
public class ApiKeyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiKeyServiceApplication.class, args);
    }
}