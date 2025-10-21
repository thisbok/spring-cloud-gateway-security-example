package com.exec.api.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * 📊 요청/응답 로깅 시스템 설정
 * <p>
 * application.yml 에서 로깅 관련 설정을 관리합니다.
 */
@Configuration
@ConfigurationProperties(prefix = "logging.request-response")
@Getter
@Setter
public class RequestResponseLoggingConfig {

    /**
     * 로깅 시스템 활성화 여부
     */
    private boolean enabled = true;

    /**
     * 요청/응답 바디 최대 크기 (바이트)
     */
    private int maxBodySize = 10240; // 10KB

    /**
     * 헤더 값 최대 길이
     */
    private int maxHeaderValueLength = 500;

    /**
     * 민감정보 마스킹 필드 목록
     */
    private Set<String> sensitiveFields = Set.of(
            "password", "passwd", "pwd", "secret", "token", "authorization",
            "api-key", "api_key", "access-token", "access_token", "refresh-token",
            "ssn", "social", "credit-card", "creditcard", "cvv", "pin"
    );

    /**
     * 로깅 제외 경로 패턴
     */
    private Set<String> excludePaths = Set.of(
            "/actuator/**",
            "/health/**",
            "/metrics/**",
            "/favicon.ico"
    );

    /**
     * 로깅 제외 HTTP 메서드
     */
    private Set<String> excludeMethods = Set.of();

    /**
     * 저장 채널 설정
     */
    private Channels channels = new Channels();

    /**
     * 성능 설정
     */
    private Performance performance = new Performance();

    /**
     * 보안 설정
     */
    private Security security = new Security();

    /**
     * 특정 경로가 로깅 제외 대상인지 확인
     */
    public boolean isPathExcluded(String path) {
        if (path == null) return false;

        return excludePaths.stream()
                .anyMatch(pattern -> {
                    // 와일드카드 패턴 매칭
                    if (pattern.contains("**")) {
                        String prefix = pattern.replace("**", "");
                        return path.startsWith(prefix);
                    } else if (pattern.contains("*")) {
                        String prefix = pattern.replace("*", "");
                        return path.startsWith(prefix);
                    } else {
                        return path.equals(pattern);
                    }
                });
    }

    /**
     * 특정 HTTP 메서드가 로깅 제외 대상인지 확인
     */
    public boolean isMethodExcluded(String method) {
        if (method == null) return false;
        return excludeMethods.contains(method.toUpperCase());
    }

    /**
     * 특정 필드가 민감정보인지 확인
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;

        String lowerFieldName = fieldName.toLowerCase();
        return sensitiveFields.stream()
                .anyMatch(sensitiveField -> lowerFieldName.contains(sensitiveField));
    }

    /**
     * 로깅이 활성화되어 있고 경로/메서드가 제외되지 않았는지 확인
     */
    public boolean shouldLog(String method, String path) {
        return enabled &&
                !isMethodExcluded(method) &&
                !isPathExcluded(path);
    }

    /**
     * 저장 채널이 하나라도 활성화되어 있는지 확인
     */
    public boolean hasActiveChannel() {
        return channels.mysql || channels.elasticsearch || channels.kafka;
    }

    /**
     * 설정 유효성 검증
     */
    public void validate() {
        if (enabled && !hasActiveChannel()) {
            throw new IllegalStateException("로깅이 활성화되어 있지만 활성화된 저장 채널이 없습니다.");
        }

        if (maxBodySize < 0) {
            throw new IllegalArgumentException("maxBodySize 는 0 이상이어야 합니다.");
        }

        if (maxHeaderValueLength < 0) {
            throw new IllegalArgumentException("maxHeaderValueLength 는 0 이상이어야 합니다.");
        }

        if (performance.batchSize <= 0) {
            throw new IllegalArgumentException("batchSize 는 1 이상이어야 합니다.");
        }

        if (performance.timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs 는 1 이상이어야 합니다.");
        }
    }

    @Getter
    @Setter
    public static class Channels {
        /**
         * MySQL 저장 활성화
         */
        private boolean mysql = true;

        /**
         * Elasticsearch 저장 활성화
         */
        private boolean elasticsearch = true;

        /**
         * Kafka 전송 활성화
         */
        private boolean kafka = true;

        /**
         * 로컬 파일 백업 활성화
         */
        private boolean fileBackup = true;
    }

    @Getter
    @Setter
    public static class Performance {
        /**
         * 비동기 처리 활성화
         */
        private boolean asyncEnabled = true;

        /**
         * 배치 처리 크기
         */
        private int batchSize = 100;

        /**
         * 배치 처리 간격 (밀리초)
         */
        private long batchIntervalMs = 5000;

        /**
         * 타임아웃 설정 (밀리초)
         */
        private long timeoutMs = 30000;

        /**
         * 재시도 횟수
         */
        private int retryCount = 3;

        /**
         * 백프레셔 임계값
         */
        private int backpressureThreshold = 1000;
    }

    @Getter
    @Setter
    public static class Security {
        /**
         * 민감정보 마스킹 활성화
         */
        private boolean maskSensitiveData = true;

        /**
         * IP 주소 마스킹 활성화
         */
        private boolean maskIpAddress = false;

        /**
         * 사용자 정보 마스킹 활성화
         */
        private boolean maskUserInfo = true;

        /**
         * 신용카드 정보 마스킹 활성화
         */
        private boolean maskCreditCard = true;

        /**
         * 허용된 IP 대역 (로깅 제외)
         */
        private List<String> allowedIpRanges = List.of();

        /**
         * 보안 스캔 활성화 (추가 보안 검사)
         */
        private boolean securityScanEnabled = false;
    }
}