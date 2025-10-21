package com.exec.api.gateway.security.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 보안 감사 이벤트
 * <p>
 * Kafka 로 전송되는 보안 관련 모든 이벤트를 정의합니다.
 * requestId 를 통해 요청 추적을 지원합니다.
 *
 * @since 1.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAuditEvent {

    private String requestId;  // 요청 추적 ID (X-Request-ID)
    private String accessKey;  // API Access Key (주요 식별자)
    private EventType eventType;
    private String source;
    private String ipAddress;
    private String userAgent;
    private String method;
    private String uri;
    private String description;
    private Map<String, Object> additionalData;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private Long processingTimeMs;
    private Integer statusCode;
    private String errorMessage;

    // 지리적 정보
    private String country;
    private String region;
    private String city;

    // 보안 컨텍스트
    private String sessionId;
    private String deviceFingerprint;

    /**
     * 인증 성공 이벤트 생성
     */
    public static SecurityAuditEvent authenticationSuccess(String requestId, String accessKey, String ipAddress) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)
                .eventType(EventType.AUTHENTICATION_SUCCESS)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("User authentication successful")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 인증 실패 이벤트 생성
     */
    public static SecurityAuditEvent authenticationFailure(String requestId, String accessKey, String ipAddress, String reason) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)
                .eventType(EventType.AUTHENTICATION_FAILURE)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("User authentication failed: " + reason)
                .errorMessage(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * API 키 검증 성공 이벤트 생성
     */
    public static SecurityAuditEvent apiKeyValidationSuccess(String requestId, String accessKey, String ipAddress) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)  // 파티션 키로 사용
                .eventType(EventType.API_KEY_VALIDATION_SUCCESS)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("API key validation successful")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * API 키 검증 실패 이벤트 생성
     */
    public static SecurityAuditEvent apiKeyValidationFailure(String requestId, String accessKey, String ipAddress, String reason) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)  // 파티션 키로 사용
                .eventType(EventType.API_KEY_VALIDATION_FAILURE)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("API key validation failed for key: " + maskApiKey(accessKey) + ", reason: " + reason)
                .errorMessage(reason)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Rate Limit 초과 이벤트 생성
     */
    public static SecurityAuditEvent rateLimitExceeded(String requestId, String accessKey, String ipAddress, String limitType) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)
                .eventType(EventType.RATE_LIMIT_EXCEEDED)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("Rate limit exceeded for " + limitType)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * DDoS 공격 탐지 이벤트 생성
     */
    public static SecurityAuditEvent ddosAttackDetected(String requestId, String ipAddress, int requestCount) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .eventType(EventType.DDOS_ATTACK_DETECTED)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .description("Potential DDoS attack detected from IP: " + ipAddress + ", request count: " + requestCount)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 보안 공격 시도 이벤트 생성
     */
    public static SecurityAuditEvent securityAttackAttempt(String requestId, EventType attackType, String ipAddress, String uri, String payload) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .eventType(attackType)
                .source("api-gateway")
                .ipAddress(ipAddress)
                .uri(uri)
                .description("Security attack attempt detected: " + attackType.name())
                .additionalData(Map.of("payload", sanitizePayload(payload)))
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * API 요청 이벤트 생성
     */
    public static SecurityAuditEvent apiRequest(String requestId, String accessKey, String method, String uri, String ipAddress, String userAgent) {
        return SecurityAuditEvent.builder()
                .requestId(requestId)
                .accessKey(accessKey)
                .eventType(EventType.API_REQUEST)
                .source("api-gateway")
                .method(method)
                .uri(uri)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .description("API request received")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 현재 요청의 Request ID 획득
     */
    public static String getCurrentRequestId() {
        // 1. MDC 에서 requestId 추출 시도
        String requestId = org.slf4j.MDC.get("requestId");
        if (requestId != null && !requestId.isEmpty()) {
            return requestId;
        }

        // 2. 다른 MDC 키들에서 추출 시도 (X-Request-ID 등)
        String[] alternativeKeys = {"X-Request-ID", "request-id"};
        for (String key : alternativeKeys) {
            String altRequestId = org.slf4j.MDC.get(key);
            if (altRequestId != null && !altRequestId.isEmpty()) {
                return altRequestId;
            }
        }

        // 3. 최종 Fallback: null 반환 (호출자가 직접 전달하도록 유도)
        return null;
    }

    /**
     * API 키 마스킹
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 공격 페이로드 정화 (민감한 정보 제거)
     */
    private static String sanitizePayload(String payload) {
        if (payload == null) return null;

        // 길이 제한
        if (payload.length() > 1000) {
            payload = payload.substring(0, 1000) + "...";
        }

        // 민감한 정보 마스킹
        return payload
                .replaceAll("(?i)(password|secret|token|key)\\s*[:=]\\s*['\"]?[^'\",\\s]+", "$1=***")
                .replaceAll("(?i)(authorization|bearer)\\s+[\\w-]+", "$1 ***");
    }

    /**
     * 이벤트 타입 (위험도 점수와 심각도 레벨 내장)
     */
    public enum EventType {
        // 인증 관련 - 낮은 위험도
        AUTHENTICATION_SUCCESS(EventLevel.INFO, 0.0, false, "User authentication successful"),
        AUTHENTICATION_FAILURE(EventLevel.WARNING, 3.0, true, "User authentication failed"),
        API_KEY_VALIDATION_SUCCESS(EventLevel.INFO, 0.0, false, "API key validation successful"),
        API_KEY_VALIDATION_FAILURE(EventLevel.ERROR, 5.0, true, "API key validation failed"),
        JWT_TOKEN_ISSUED(EventLevel.INFO, 0.0, false, "JWT token issued"),
        JWT_TOKEN_EXPIRED(EventLevel.WARNING, 1.0, false, "JWT token expired"),
        JWT_TOKEN_INVALID(EventLevel.WARNING, 2.0, true, "Invalid JWT token"),
        MFA_SUCCESS(EventLevel.INFO, 0.0, false, "Multi-factor authentication successful"),
        MFA_FAILURE(EventLevel.WARNING, 3.0, true, "Multi-factor authentication failed"),
        OAUTH_LOGIN_SUCCESS(EventLevel.INFO, 0.0, false, "OAuth login successful"),
        OAUTH_LOGIN_FAILURE(EventLevel.WARNING, 2.0, true, "OAuth login failed"),

        // 인가 관련 - 중간 위험도
        AUTHORIZATION_SUCCESS(EventLevel.INFO, 0.0, false, "Authorization successful"),
        AUTHORIZATION_FAILURE(EventLevel.WARNING, 4.0, true, "Authorization failed"),
        RBAC_VIOLATION(EventLevel.ERROR, 6.0, true, "Role-based access control violation"),
        ABAC_VIOLATION(EventLevel.ERROR, 6.0, true, "Attribute-based access control violation"),
        POLICY_EVALUATION(EventLevel.INFO, 0.0, false, "Policy evaluation completed"),

        // 보안 위협 - 높은 위험도
        RATE_LIMIT_EXCEEDED(EventLevel.WARNING, 4.0, true, "Rate limit exceeded"),
        DDOS_ATTACK_DETECTED(EventLevel.CRITICAL, 9.0, true, "Potential DDoS attack detected"),
        SUSPICIOUS_REQUEST_PATTERN(EventLevel.SECURITY, 7.0, true, "Suspicious request pattern detected"),
        SQL_INJECTION_ATTEMPT(EventLevel.CRITICAL, 8.0, true, "SQL injection attempt detected"),
        XSS_ATTEMPT(EventLevel.CRITICAL, 8.0, true, "Cross-site scripting attempt detected"),
        PATH_TRAVERSAL_ATTEMPT(EventLevel.CRITICAL, 8.0, true, "Path traversal attempt detected"),
        COMMAND_INJECTION_ATTEMPT(EventLevel.CRITICAL, 8.5, true, "Command injection attempt detected"),
        BRUTE_FORCE_ATTEMPT(EventLevel.SECURITY, 7.0, true, "Brute force attack attempt detected"),
        ANOMALY_DETECTED(EventLevel.WARNING, 5.0, true, "Anomaly detected"),
        GEO_BLOCKING_TRIGGERED(EventLevel.WARNING, 3.0, true, "Geographic blocking triggered"),

        // 시스템 이벤트 - 정보성
        API_REQUEST(EventLevel.INFO, 0.0, false, "API request received"),
        API_RESPONSE(EventLevel.INFO, 0.0, false, "API response sent"),
        SYSTEM_ERROR(EventLevel.ERROR, 2.0, false, "System error occurred"),
        CONFIGURATION_CHANGE(EventLevel.WARNING, 1.0, false, "Configuration changed"),
        POLICY_UPDATE(EventLevel.INFO, 0.0, false, "Security policy updated"),

        // 관리 이벤트 - 중간 위험도
        ADMIN_LOGIN(EventLevel.WARNING, 2.0, false, "Administrator login"),
        ADMIN_ACTION(EventLevel.WARNING, 3.0, false, "Administrator action performed"),
        USER_CREATED(EventLevel.INFO, 1.0, false, "User account created"),
        USER_DELETED(EventLevel.WARNING, 2.0, false, "User account deleted"),
        PERMISSION_CHANGED(EventLevel.WARNING, 3.0, false, "User permissions changed"),

        // 알림 이벤트 - 정보성
        SECURITY_ALERT_TRIGGERED(EventLevel.SECURITY, 6.0, true, "Security alert triggered"),
        THRESHOLD_EXCEEDED(EventLevel.WARNING, 4.0, true, "Threshold exceeded"),
        SERVICE_HEALTH_CHECK(EventLevel.INFO, 0.0, false, "Service health check");

        private final EventLevel level;
        private final Double riskScore;
        private final Boolean suspiciousActivity;
        private final String defaultDescription;

        EventType(EventLevel level, Double riskScore, Boolean suspiciousActivity, String defaultDescription) {
            this.level = level;
            this.riskScore = riskScore;
            this.suspiciousActivity = suspiciousActivity;
            this.defaultDescription = defaultDescription;
        }

        public EventLevel getLevel() {
            return level;
        }

        public Double getRiskScore() {
            return riskScore;
        }

        public Boolean getSuspiciousActivity() {
            return suspiciousActivity;
        }

        public String getDefaultDescription() {
            return defaultDescription;
        }

        /**
         * 위험도가 높은 이벤트인지 판단 (위험도 5.0 이상)
         */
        public boolean isHighRisk() {
            return riskScore >= 5.0;
        }

        /**
         * 즉시 알림이 필요한 이벤트인지 판단
         */
        public boolean requiresImmediateAlert() {
            return level == EventLevel.CRITICAL || level == EventLevel.SECURITY;
        }

        /**
         * 보안 관련 이벤트인지 판단
         */
        public boolean isSecurityRelated() {
            return suspiciousActivity || level == EventLevel.SECURITY || level == EventLevel.CRITICAL;
        }
    }

    /**
     * 이벤트 심각도 레벨
     */
    public enum EventLevel {
        INFO,      // 일반적인 정보성 이벤트
        WARNING,   // 주의가 필요한 이벤트
        ERROR,     // 오류 이벤트
        CRITICAL,  // 즉시 대응이 필요한 중요 이벤트
        SECURITY   // 보안 관련 중요 이벤트
    }
}