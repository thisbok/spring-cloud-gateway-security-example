package com.exec.services.analytics.service;

import com.exec.services.analytics.config.AnalyticsConfig;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 📊 실시간 분석 서비스
 * <p>
 * Redis 를 활용한 실시간 메트릭 수집 및 분석:
 * 1. 실시간 카운터 (요청수, 에러수, 응답시간)
 * 2. 슬라이딩 윈도우 메트릭 (분/시간/일별)
 * 3. 이상 징후 탐지 (급증, 급감, 임계값 초과)
 * 4. 알림 트리거 (실시간 모니터링)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeAnalyticsService {

    // Redis 키 패턴 상수
    private static final String COUNTER_PREFIX = "analytics:counter:";
    private static final String SLIDING_WINDOW_PREFIX = "analytics:window:";
    private static final String ALERT_CONFIG_PREFIX = "analytics:alert:";
    private static final String ANOMALY_DETECTION_PREFIX = "analytics:anomaly:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalyticsConfig analyticsConfig;
    private final MetricsService metricsService;
    private final AlertService alertService;

    /**
     * API 호출 로그 실시간 처리
     */
    @Async("analyticsExecutor")
    public void processApiCallLog(ApiGatewayRequestLogDto logDto) {
        try {
            log.debug("실시간 분석 처리: {}", logDto.getRequestId());

            // 카운터 업데이트
            incrementCounters(logDto);

            // 슬라이딩 윈도우 업데이트
            updateSlidingWindow(logDto);

            // 응답 시간 분석
            analyzeResponseTime(logDto);

            // 에러율 분석
            if (logDto.getHasError()) {
                analyzeErrorRate(logDto);
            }

            // 트래픽 패턴 분석
            analyzeTrafficPattern(logDto);

        } catch (Exception e) {
            log.error("실시간 분석 처리 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 실시간 카운터 증가
     */
    public void incrementCounters(ApiGatewayRequestLogDto logDto) {
        try {
            // accessKey 기반 모니터링 (인증 실패 시에도 추적 가능)
            String accessKey = getMonitoringKey(logDto);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"));

            // 전체 요청 카운터
            incrementCounter("total_requests", accessKey, timestamp);

            // 성공/실패 카운터
            if (logDto.getIsSuccess()) {
                incrementCounter("success_requests", accessKey, timestamp);
            } else {
                incrementCounter("error_requests", accessKey, timestamp);
            }

            // HTTP 메서드별 카운터
            incrementCounter("method_" + logDto.getMethod(), accessKey, timestamp);

            // 상태 코드별 카운터
            incrementCounter("status_" + logDto.getStatusCode(), accessKey, timestamp);

            // 엔드포인트별 카운터
            String endpoint = sanitizeEndpoint(logDto.getEndpoint());
            incrementCounter("endpoint_" + endpoint, accessKey, timestamp);

            // 응답 시간 누적
            updateResponseTimeMetrics(logDto);

            log.debug("카운터 업데이트 완료: {}", accessKey);

        } catch (Exception e) {
            log.error("카운터 업데이트 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 슬라이딩 윈도우 메트릭 업데이트
     */
    public void updateSlidingWindow(ApiGatewayRequestLogDto logDto) {
        try {
            String accessKey = getMonitoringKey(logDto);
            long timestamp = System.currentTimeMillis();

            // 설정된 윈도우 크기별로 업데이트
            for (String window : analyticsConfig.getMetrics().getRealtime().getWindowSizes()) {
                updateWindowMetric(accessKey, window, timestamp, logDto);
            }

            log.debug("슬라이딩 윈도우 업데이트 완료: {}", accessKey);

        } catch (Exception e) {
            log.error("슬라이딩 윈도우 업데이트 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 에러율 급증 체크
     */
    public void checkErrorRateSpike(String accessKey) {
        try {
            double threshold = analyticsConfig.getMetrics().getThresholds().getErrorRate();

            // 최근 5 분간 에러율 계산
            double currentErrorRate = calculateErrorRate(accessKey, "5m");

            // 임계값 초과 체크
            if (currentErrorRate > threshold) {
                alertService.triggerErrorRateAlert(accessKey, currentErrorRate, threshold);
            }

            // 메트릭 기록
            metricsService.recordBusinessMetric("error_rate", currentErrorRate, "accessKey", accessKey);

        } catch (Exception e) {
            log.error("에러율 체크 실패: {}", accessKey, e);
        }
    }

    /**
     * 트래픽 급증 체크
     */
    public void checkTrafficSpike(String accessKey) {
        try {
            double spikeMultiplier = analyticsConfig.getMetrics().getThresholds().getTrafficSpike();

            // 현재 1 분간 요청수
            long currentRequests = getWindowMetric(accessKey, "1m", "total_requests");

            // 지난 1 시간 평균 요청수 (분당)
            long avgRequests = getWindowMetric(accessKey, "1h", "total_requests") / 60;

            // 급증 탐지 (평균의 N 배 이상)
            if (avgRequests > 0 && currentRequests > avgRequests * spikeMultiplier) {
                alertService.triggerTrafficSpikeAlert(accessKey, currentRequests, avgRequests);
            }

            // 메트릭 기록
            metricsService.recordBusinessMetric("traffic_current", currentRequests, "accessKey", accessKey);
            metricsService.recordBusinessMetric("traffic_avg", avgRequests, "accessKey", accessKey);

        } catch (Exception e) {
            log.error("트래픽 급증 체크 실패: {}", accessKey, e);
        }
    }

    /**
     * 실시간 대시보드 데이터 조회
     */
    public RealTimeDashboardDto getRealTimeDashboard(String accessKey) {
        try {
            return RealTimeDashboardDto.builder()
                    .accessKey(accessKey)
                    .currentRpm(getWindowMetric(accessKey, "1m", "total_requests"))
                    .currentErrorRate(calculateErrorRate(accessKey, "5m"))
                    .avgResponseTime(getAverageResponseTime(accessKey, "5m"))
                    .activeConnections(getActiveConnections(accessKey))
                    .topEndpoints(getTopEndpoints(accessKey, 5))
                    .recentErrors(getRecentErrors(accessKey, 10))
                    .alertStatus(getAlertStatus(accessKey))
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("실시간 대시보드 데이터 조회 실패: {}", accessKey, e);
            return null;
        }
    }

    // ==================== Private Methods ====================

    /**
     * 모니터링 키 추출 (fallback 전략)
     * <p>
     * 우선순위:
     * 1. accessKey (인증 실패 시에도 존재, API Key 별 집계 가능)
     * 2. clientId (인증 성공 시에만 존재)
     * 3. IP 주소 (마지막 fallback)
     */
    private String getMonitoringKey(ApiGatewayRequestLogDto logDto) {
        if (logDto.getAccessKey() != null && !logDto.getAccessKey().isEmpty()) {
            return logDto.getAccessKey();
        }
        if (logDto.getClientId() != null && !logDto.getClientId().isEmpty()) {
            return "client:" + logDto.getClientId();
        }
        if (logDto.getClientIp() != null && !logDto.getClientIp().isEmpty()) {
            return "ip:" + logDto.getClientIp();
        }
        return "unknown";
    }

    /**
     * 카운터 증가 (Redis)
     */
    private void incrementCounter(String metric, String accessKey, String timestamp) {
        String key = COUNTER_PREFIX + accessKey + ":" + metric + ":" + timestamp;
        redisTemplate.opsForValue().increment(key);

        Duration ttl = analyticsConfig.getMetrics().getRedis().getCounterTtl();
        redisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 윈도우 메트릭 업데이트
     */
    private void updateWindowMetric(String accessKey, String window, long timestamp, ApiGatewayRequestLogDto logDto) {
        String baseKey = SLIDING_WINDOW_PREFIX + accessKey + ":" + window + ":";

        // 요청수 카운터
        redisTemplate.opsForZSet().add(baseKey + "requests", timestamp, timestamp);

        // 에러 카운터 (에러인 경우만)
        if (logDto.getHasError()) {
            redisTemplate.opsForZSet().add(baseKey + "errors", timestamp, timestamp);
        }

        // 응답시간 (히스토그램용)
        if (logDto.getResponseTimeMs() != null) {
            redisTemplate.opsForZSet().add(baseKey + "response_times",
                    logDto.getResponseTimeMs(), timestamp);
        }

        // TTL 설정 (윈도우 크기의 N 배)
        Duration windowDuration = getWindowDuration(window);
        int multiplier = analyticsConfig.getMetrics().getRedis().getWindowTtlMultiplier();
        Duration ttl = windowDuration.multipliedBy(multiplier);

        redisTemplate.expire(baseKey + "requests", ttl.toSeconds(), TimeUnit.SECONDS);
        redisTemplate.expire(baseKey + "errors", ttl.toSeconds(), TimeUnit.SECONDS);
        redisTemplate.expire(baseKey + "response_times", ttl.toSeconds(), TimeUnit.SECONDS);

        // 윈도우 정리 (오래된 데이터 제거)
        cleanupWindow(baseKey, window, timestamp);
    }

    /**
     * 응답 시간 분석
     */
    private void analyzeResponseTime(ApiGatewayRequestLogDto logDto) {
        if (logDto.getResponseTimeMs() == null) return;

        try {
            long threshold = analyticsConfig.getMetrics().getThresholds().getResponseTime();

            // 응답 시간 임계값 초과 체크
            if (logDto.getResponseTimeMs() > threshold) {
                alertService.triggerSlowResponseAlert(logDto, threshold);
            }

            // 응답 시간 히스토그램 업데이트
            updateResponseTimeHistogram(logDto);

        } catch (Exception e) {
            log.error("응답 시간 분석 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 에러율 분석
     */
    private void analyzeErrorRate(ApiGatewayRequestLogDto logDto) {
        try {
            String accessKey = getMonitoringKey(logDto);

            // 최근 에러 패턴 분석
            analyzeErrorPattern(logDto);

            // 연속 에러 탐지
            checkConsecutiveErrors(accessKey);

        } catch (Exception e) {
            log.error("에러율 분석 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 트래픽 패턴 분석
     */
    private void analyzeTrafficPattern(ApiGatewayRequestLogDto logDto) {
        try {
            // 시간대별 패턴 분석
            analyzeHourlyPattern(logDto);

            // 지역별 트래픽 분석
            analyzeGeographicPattern(logDto);

            // 디바이스별 트래픽 분석
            analyzeUserAgentPattern(logDto);

        } catch (Exception e) {
            log.error("트래픽 패턴 분석 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 응답 시간 메트릭 업데이트
     */
    private void updateResponseTimeMetrics(ApiGatewayRequestLogDto logDto) {
        if (logDto.getResponseTimeMs() == null) return;

        String accessKey = getMonitoringKey(logDto);
        String key = COUNTER_PREFIX + accessKey + ":response_times";

        // 응답 시간 누적
        redisTemplate.opsForValue().increment(key + ":sum", logDto.getResponseTimeMs());
        redisTemplate.opsForValue().increment(key + ":count");

        // TTL 설정
        Duration ttl = analyticsConfig.getMetrics().getRedis().getCounterTtl();
        redisTemplate.expire(key + ":sum", ttl.toSeconds(), TimeUnit.SECONDS);
        redisTemplate.expire(key + ":count", ttl.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 에러율 계산
     */
    private double calculateErrorRate(String accessKey, String window) {
        long totalRequests = getWindowMetric(accessKey, window, "total_requests");
        long errorRequests = getWindowMetric(accessKey, window, "error_requests");

        if (totalRequests == 0) return 0.0;
        return (double) errorRequests / totalRequests;
    }

    /**
     * 윈도우 메트릭 조회
     */
    private long getWindowMetric(String accessKey, String window, String metric) {
        String key = SLIDING_WINDOW_PREFIX + accessKey + ":" + window + ":" + metric;

        long cutoff = System.currentTimeMillis() - getWindowDuration(window).toMillis();

        return redisTemplate.opsForZSet().count(key, cutoff, System.currentTimeMillis());
    }

    /**
     * 평균 응답 시간 계산
     */
    private double getAverageResponseTime(String accessKey, String window) {
        String key = COUNTER_PREFIX + accessKey + ":response_times";

        Object sumObj = redisTemplate.opsForValue().get(key + ":sum");
        Object countObj = redisTemplate.opsForValue().get(key + ":count");

        if (sumObj == null || countObj == null) return 0.0;

        long sum = Long.parseLong(sumObj.toString());
        long count = Long.parseLong(countObj.toString());

        return count > 0 ? (double) sum / count : 0.0;
    }

    /**
     * 윈도우 지속 시간 변환
     */
    private Duration getWindowDuration(String window) {
        return switch (window) {
            case "1m" -> Duration.ofMinutes(1);
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            default -> Duration.ofMinutes(5);
        };
    }

    /**
     * 윈도우 정리 (오래된 데이터 제거)
     */
    private void cleanupWindow(String baseKey, String window, long currentTimestamp) {
        long cutoff = currentTimestamp - getWindowDuration(window).toMillis();

        redisTemplate.opsForZSet().removeRangeByScore(baseKey + "requests", 0, cutoff);
        redisTemplate.opsForZSet().removeRangeByScore(baseKey + "errors", 0, cutoff);
        redisTemplate.opsForZSet().removeRangeByScore(baseKey + "response_times", 0, cutoff);
    }

    /**
     * 엔드포인트 정규화
     */
    private String sanitizeEndpoint(String endpoint) {
        if (endpoint == null) return "unknown";

        return endpoint
                .replaceAll("/\\d+", "/{id}")
                .replaceAll("/[a-f0-9-]{36}", "/{uuid}")
                .replace("/", "_")
                .replaceAll("[^a-zA-Z0-9_]", "");
    }

    // Stub 구현 메서드들
    private long getActiveConnections(String accessKey) {
        return 42L; // TODO: 실제 구현
    }

    private Map<String, Long> getTopEndpoints(String accessKey, int limit) {
        Map<String, Long> topEndpoints = new HashMap<>();
        topEndpoints.put("/api/v1/payments", 150L);
        topEndpoints.put("/api/v1/users", 89L);
        return topEndpoints;
    }

    private java.util.List<String> getRecentErrors(String accessKey, int limit) {
        return java.util.List.of("500 - Internal Server Error", "429 - Too Many Requests");
    }

    private String getAlertStatus(String accessKey) {
        return "NORMAL"; // NORMAL, WARNING, CRITICAL
    }

    private void updateResponseTimeHistogram(ApiGatewayRequestLogDto logDto) {
        // TODO: 히스토그램 업데이트
    }

    private void analyzeErrorPattern(ApiGatewayRequestLogDto logDto) {
        // TODO: 에러 패턴 분석
    }

    private void checkConsecutiveErrors(String accessKey) {
        // TODO: 연속 에러 체크
    }

    private void analyzeHourlyPattern(ApiGatewayRequestLogDto logDto) {
        // TODO: 시간대별 패턴 분석
    }

    private void analyzeGeographicPattern(ApiGatewayRequestLogDto logDto) {
        // TODO: 지역별 패턴 분석
    }

    private void analyzeUserAgentPattern(ApiGatewayRequestLogDto logDto) {
        // TODO: User-Agent 패턴 분석
    }

    /**
     * 실시간 대시보드 DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class RealTimeDashboardDto {
        private String accessKey;          // 모니터링 대상 Access Key
        private long currentRpm;           // 현재 분당 요청수
        private double currentErrorRate;   // 현재 에러율
        private double avgResponseTime;    // 평균 응답시간
        private long activeConnections;    // 활성 연결수
        private Map<String, Long> topEndpoints;     // 상위 엔드포인트
        private java.util.List<String> recentErrors; // 최근 에러
        private String alertStatus;        // 알림 상태
        private LocalDateTime timestamp;   // 수집 시간
    }
}