package com.exec.common.constants;

/**
 * Kafka Topic 상수 정의
 * 모든 프로젝트에서 동일한 Topic 이름을 사용하기 위한 중앙 집중 관리
 */
public final class KafkaTopics {

    /**
     * API 호출 이벤트 토픽
     * Gateway 에서 모든 API 호출에 대한 메타데이터 발행
     */
    public static final String API_CALL_EVENTS = "api-call-events";

    // ===== API Gateway 이벤트 =====
    /**
     * 인증 이벤트 토픽
     * API Key, JWT 등 인증 관련 이벤트 발행
     */
    public static final String AUTHENTICATION_EVENTS = "authentication-events";
    /**
     * 보안 이벤트 토픽
     * Rate Limiting, DDoS 탐지, 보안 위협 관련 이벤트
     */
    public static final String SECURITY_EVENTS = "security-events";
    /**
     * API Key 관리 이벤트 토픽
     * API Key 생성, 수정, 삭제, 활성화/비활성화 이벤트
     */
    public static final String API_KEY_EVENTS = "api-key-events";

    // ===== 비즈니스 도메인 이벤트 =====
    /**
     * 결제 이벤트 토픽
     * 결제 요청, 처리, 완료, 실패 관련 이벤트
     */
    public static final String PAYMENT_EVENTS = "payment-events";
    /**
     * 사용자 이벤트 토픽
     * 사용자 생성, 수정, 삭제, 상태 변경 이벤트
     */
    public static final String USER_EVENTS = "user-events";
    /**
     * 고객사 이벤트 토픽
     * 고객사 등록, 설정 변경, 계약 관련 이벤트
     */
    public static final String CLIENT_EVENTS = "client-events";
    /**
     * 알림 이벤트 토픽
     * 이메일, SMS, 푸시 알림 발송 요청
     */
    public static final String NOTIFICATION_EVENTS = "notification-events";

    // ===== 알림 및 통신 =====
    /**
     * 웹훅 이벤트 토픽
     * 외부 서비스로부터 수신된 웹훅 이벤트
     */
    public static final String WEBHOOK_EVENTS = "webhook-events";
    /**
     * 외부 콜백 이벤트 토픽
     * 고객사로 전송할 콜백 이벤트
     */
    public static final String CALLBACK_EVENTS = "callback-events";
    /**
     * 감사 로그 이벤트 토픽
     * 시스템 감사를 위한 모든 중요 이벤트 로깅
     */
    public static final String AUDIT_EVENTS = "audit-events";

    // ===== 시스템 운영 =====
    /**
     * 시스템 메트릭 이벤트 토픽
     * 성능, 사용량 등 시스템 메트릭 데이터
     */
    public static final String METRICS_EVENTS = "metrics-events";
    /**
     * 배치 작업 이벤트 토픽
     * 스케줄 작업, 배치 처리 관련 이벤트
     */
    public static final String BATCH_JOB_EVENTS = "batch-job-events";
    /**
     * API Key 무효화 이벤트 토픽
     * API Key 캐시 무효화 등 관련 이벤트
     */
    public static final String API_KEY_INVALIDATION = "api-key-invalidation";
    /**
     * 시스템 알림 이벤트 토픽
     * 시스템 전체 알림, 유지보수 공지 등
     */
    public static final String SYSTEM_NOTIFICATIONS = "system-notifications";
    /**
     * 설정 변경 이벤트 토픽
     * 런타임 설정 변경, 정책 업데이트 등
     */
    public static final String CONFIGURATION_CHANGES = "configuration-changes";
    /**
     * 실패한 이벤트 재처리를 위한 DLQ 토픽
     */
    public static final String DLQ_EVENTS = "dlq-events";

    // ===== Dead Letter Queue =====
    private KafkaTopics() {
        // 인스턴스 생성 방지
    }

    // ===== 토픽 그룹별 상수 =====

    /**
     * 환경별 토픽 이름 생성 유틸리티
     *
     * @param topic       기본 토픽 이름
     * @param environment 환경 (local, develop, production)
     * @return 환경이 포함된 토픽 이름
     */
    public static String withEnvironment(String topic, String environment) {
        if (environment == null || environment.trim().isEmpty()) {
            return topic;
        }
        return environment.toLowerCase() + "-" + topic;
    }

    /**
     * 모든 토픽 이름 목록 반환
     *
     * @return 정의된 모든 토픽 이름 배열
     */
    public static String[] getAllTopics() {
        return new String[]{
                API_CALL_EVENTS,
                AUTHENTICATION_EVENTS,
                SECURITY_EVENTS,
                API_KEY_EVENTS,
                PAYMENT_EVENTS,
                USER_EVENTS,
                CLIENT_EVENTS,
                NOTIFICATION_EVENTS,
                WEBHOOK_EVENTS,
                CALLBACK_EVENTS,
                AUDIT_EVENTS,
                METRICS_EVENTS,
                BATCH_JOB_EVENTS,
                API_KEY_INVALIDATION,
                SYSTEM_NOTIFICATIONS,
                CONFIGURATION_CHANGES,
                DLQ_EVENTS
        };
    }

    /**
     * Gateway 관련 토픽 목록
     */
    public static final class Gateway {
        public static final String API_CALLS = API_CALL_EVENTS;
        public static final String AUTHENTICATION = AUTHENTICATION_EVENTS;
        public static final String SECURITY = SECURITY_EVENTS;

        private Gateway() {
        }
    }

    /**
     * 비즈니스 도메인 토픽 목록
     */
    public static final class Domain {
        public static final String API_KEYS = API_KEY_EVENTS;
        public static final String PAYMENTS = PAYMENT_EVENTS;
        public static final String USERS = USER_EVENTS;
        public static final String CLIENTS = CLIENT_EVENTS;

        private Domain() {
        }
    }

    // ===== 환경별 토픽 Prefix 유틸리티 =====

    /**
     * 알림 관련 토픽 목록
     */
    public static final class Communication {
        public static final String NOTIFICATIONS = NOTIFICATION_EVENTS;
        public static final String WEBHOOKS = WEBHOOK_EVENTS;
        public static final String CALLBACKS = CALLBACK_EVENTS;

        private Communication() {
        }
    }

    /**
     * 시스템 운영 토픽 목록
     */
    public static final class System {
        public static final String AUDIT = AUDIT_EVENTS;
        public static final String METRICS = METRICS_EVENTS;
        public static final String BATCH_JOBS = BATCH_JOB_EVENTS;
        public static final String DLQ = DLQ_EVENTS;

        private System() {
        }
    }
}