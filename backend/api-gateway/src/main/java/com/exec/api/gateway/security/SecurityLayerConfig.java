package com.exec.api.gateway.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 보안 계층 설정
 * <p>
 * 🎯 핵심 보안 계층 설정 관리
 * 1. 사전 검증 계층: Rate Limiting, DDoS 방어, Input Validation
 * 2. 인증 계층: API Key, OAuth 2.0/OIDC, MFA
 * 3. 인가 계층: RBAC, ABAC, Policy Decision Point
 * 4. 모니터링 계층: Audit Logging, Security Event Monitoring, Alert System
 */
@Component
@ConfigurationProperties(prefix = "security.layers")
@Getter
@Setter
public class SecurityLayerConfig {

    private PreValidation preValidation = new PreValidation();
    private Authentication authentication = new Authentication();
    private Authorization authorization = new Authorization();
    private Monitoring monitoring = new Monitoring();

    @Getter
    @Setter
    public static class PreValidation {
        private InputValidation inputValidation = new InputValidation();

        @Getter
        @Setter
        public static class InputValidation {
            private boolean sqlInjectionProtection = true;
            private boolean xssProtection = true;
            private boolean pathTraversalProtection = true;
            private boolean commandInjectionProtection = true;
            private int maxRequestSize = 10485760; // 10MB
            private String[] allowedContentTypes = {"application/json", "application/x-www-form-urlencoded"};
        }
    }

    @Getter
    @Setter
    public static class Authentication {
        private APIKey apiKey = new APIKey();
        private OAuth oauth = new OAuth();
        private MFA mfa = new MFA();

        @Getter
        @Setter
        public static class APIKey {
            private boolean enabled = true;
            private boolean requireSignature = true;
            private long timestampToleranceSeconds = 300; // 5 분
            private String hashAlgorithm = "HMAC-SHA256";

            // 검증 및 캐시 설정
            private ValidationStrategy validationStrategy = ValidationStrategy.CACHE_FIRST;
            private CacheConfig cache = new CacheConfig();
            private SecurityConfig security = new SecurityConfig();

            public enum ValidationStrategy {
                CACHE_FIRST,     // 캐시 우선 검증
                ALWAYS_VALIDATE, // 항상 DB 검증
            }

            @Getter
            @Setter
            public static class CacheConfig {
                private boolean enabled = true;
                private long l1TtlMinutes = 1;      // Local cache TTL (분)
                private long l2TtlMinutes = 5;      // Redis cache TTL (분)
                private int l1MaxSize = 10000;      // Local cache 최대 크기
                private int l2MaxSize = 100000;     // Redis cache 최대 크기
                private boolean warmupOnStartup = true;  // 시작 시 워밍업
                private boolean preloadFrequentKeys = true; // 빈번히 사용되는 키 사전 로드
            }

            @Getter
            @Setter
            public static class SecurityConfig {
                private boolean requireSecret = false;  // Secret 필수 여부
                private boolean ipWhitelistEnabled = false;  // IP 화이트리스트 활성화
                private int maxFailedAttempts = 10;         // 최대 실패 시도 횟수
                private long blockDurationMinutes = 30;     // 차단 지속 시간 (분)
                private boolean bruteForceProtection = true; // 무차별 대입 공격 방어
            }
        }

        @Getter
        @Setter
        public static class OAuth {
            private boolean enabled = false;
            private String[] supportedProviders = {"google", "github", "kakao"};
            private String redirectUri = "/oauth2/callback";
            private long stateExpirationSeconds = 600; // 10 분
        }

        @Getter
        @Setter
        public static class MFA {
            private boolean enabled = false;
            private boolean totpEnabled = true;
            private boolean smsEnabled = true;
            private boolean emailEnabled = true;
            private int codeExpirationSeconds = 300; // 5 분
        }
    }

    @Getter
    @Setter
    public static class Authorization {
        private RBAC rbac = new RBAC();
        private ABAC abac = new ABAC();
        private PolicyEngine policyEngine = new PolicyEngine();

        @Getter
        @Setter
        public static class RBAC {
            private boolean enabled = true;
            private String[] defaultRoles = {"USER", "ADMIN", "SUPER_ADMIN"};
            private boolean hierarchicalRoles = true;
        }

        @Getter
        @Setter
        public static class ABAC {
            private boolean enabled = true;
            private boolean contextAware = true;
            private boolean timeBasedAccess = true;
            private boolean locationBasedAccess = true;
        }

        @Getter
        @Setter
        public static class PolicyEngine {
            private boolean enabled = true;
            private String policyFormat = "JSON"; // JSON, XACML
            private boolean cachePolicies = true;
            private long policyCacheTtlSeconds = 3600; // 1 시간
        }
    }

    @Getter
    @Setter
    public static class Monitoring {
        private AuditLogging auditLogging = new AuditLogging();
        private SecurityEventMonitoring eventMonitoring = new SecurityEventMonitoring();
        private AlertSystem alertSystem = new AlertSystem();

        @Getter
        @Setter
        public static class AuditLogging {
            private boolean enabled = true;
            private boolean useKafka = true;
            private String kafkaTopic = "security-audit-logs";
            private boolean logAllRequests = false;          // 모든 요청 로깅 (성능 고려)
            private boolean logFailedAuthentications = true;  // 인증 실패 로깅
            private boolean logAuthorizationFailures = true;  // 인가 실패 로깅
            private boolean logSecurityEvents = true;        // 보안 이벤트 로깅
            private String[] sensitiveFields = {"password", "secret"};
            private int batchSize = 100;                     // 배치 처리 크기
            private long flushIntervalMs = 5000;             // 강제 플러시 간격 (ms)
        }

        @Getter
        @Setter
        public static class SecurityEventMonitoring {
            private boolean enabled = true;
            private boolean realTimeAnalysis = true;
            private int anomalyDetectionWindow = 300;        // 이상 탐지 윈도우 (초)
            private double anomalyThreshold = 2.0;           // 표준편차 배수
            private boolean machineLearning = false;         // ML 기반 이상 탐지
            private String[] watchedEvents = {               // 모니터링 대상 이벤트
                    "AUTHENTICATION_FAILURE",
                    "RATE_LIMIT_EXCEEDED",
                    "SUSPICIOUS_REQUEST_PATTERN",
                    "BRUTE_FORCE_ATTEMPT"
            };
        }

        @Getter
        @Setter
        public static class AlertSystem {
            private boolean enabled = true;
            private String[] channels = {"email", "slack", "webhook", "sms"};
            private int criticalAlertResponseTimeSeconds = 60;   // 심각 알림 응답 시간
            private int warningAlertResponseTimeSeconds = 300;   // 경고 알림 응답 시간
            private boolean deduplication = true;               // 중복 알림 제거
            private long deduplicationWindowSeconds = 300;      // 중복 제거 윈도우 (초)
            private int maxAlertsPerMinute = 10;                // 분당 최대 알림 수
        }
    }
}