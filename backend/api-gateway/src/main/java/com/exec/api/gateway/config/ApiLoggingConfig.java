package com.exec.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 🚀 API 로깅 시스템 설정
 * <p>
 * 비동기 로깅 처리를 위한 스레드 풀 및 성능 최적화 설정
 */
@Configuration
@EnableAsync
@Slf4j
public class ApiLoggingConfig {

    /**
     * API 로깅 전용 비동기 실행자
     * <p>
     * 특징:
     * - 코어 스레드: 5 개 (기본 처리)
     * - 최대 스레드: 20 개 (피크 시간 대응)
     * - 큐 용량: 1000 개 (버퍼링)
     * - CallerRunsPolicy: 큐 초과 시 메인 스레드에서 실행 (백프레셔 대응)
     */
    @Bean(name = "apiLoggingExecutor")
    public Executor apiLoggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 설정
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(1000);

        // 스레드 이름 설정
        executor.setThreadNamePrefix("ApiLog-");

        // 스레드 수명 설정 (유휴 스레드 정리)
        executor.setKeepAliveSeconds(60);

        // 백프레셔 정책: 큐가 가득 찬 경우 호출자 스레드에서 실행
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 애플리케이션 종료 시 대기 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("API Logging Executor initialized - Core: {}, Max: {}, Queue: {}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity()
        );

        return executor;
    }

    /**
     * Gateway 전용 비동기 실행자 (다른 Gateway 작업용)
     */
    @Bean(name = "gatewayExecutor")
    public Executor gatewayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Gateway-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("Gateway Executor initialized - Core: {}, Max: {}, Queue: {}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity()
        );

        return executor;
    }

    /**
     * 메트릭 수집 전용 비동기 실행자
     */
    @Bean(name = "metricsExecutor")
    public Executor metricsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("Metrics-");
        executor.setKeepAliveSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);

        executor.initialize();

        log.info("Metrics Executor initialized - Core: {}, Max: {}, Queue: {}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity()
        );

        return executor;
    }
}