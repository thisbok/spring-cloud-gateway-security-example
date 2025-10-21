package com.exec.services.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 📊 Analytics Service Application
 * <p>
 * API 로그 데이터의 실시간 분석 및 메트릭 수집을 담당하는 서비스:
 * <p>
 * 주요 기능:
 * 1. Kafka 에서 API 로그 스트림 소비
 * 2. Redis 기반 실시간 메트릭 수집
 * 3. Elasticsearch 배치 인덱싱
 * 4. 이상 징후 탐지 및 알림
 * 5. 실시간 대시보드 데이터 제공
 * <p>
 * 포트: 18082
 */
@SpringBootApplication(scanBasePackages = {
        "com.exec.services.analytics",
        "com.exec.core.config",
        "com.exec.common"
})
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableElasticsearchRepositories(basePackages = "com.exec.services.analytics.repository")
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}