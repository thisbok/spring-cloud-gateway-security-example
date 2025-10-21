package com.exec.services.analytics.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 📊 메트릭 서비스
 * <p>
 * Micrometer 를 활용한 비즈니스 메트릭 수집
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    /**
     * 비즈니스 메트릭 기록
     */
    public void recordBusinessMetric(String metricName, double value, String... tags) {
        try {
            Counter.builder("analytics.business." + metricName)
                    .tags(tags)
                    .register(meterRegistry)
                    .increment(value);

        } catch (Exception e) {
            log.error("메트릭 기록 실패: {}", metricName, e);
        }
    }

    /**
     * 처리 시간 기록
     */
    public void recordProcessingTime(String operation, long duration, String... tags) {
        try {
            Timer.builder("analytics.processing.time")
                    .tag("operation", operation)
                    .tags(tags)
                    .register(meterRegistry)
                    .record(duration, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.error("처리 시간 기록 실패: {}", operation, e);
        }
    }

    /**
     * 카운터 증가
     */
    public void incrementCounter(String counterName, String... tags) {
        try {
            Counter.builder("analytics." + counterName)
                    .tags(tags)
                    .register(meterRegistry)
                    .increment();

        } catch (Exception e) {
            log.error("카운터 증가 실패: {}", counterName, e);
        }
    }
}