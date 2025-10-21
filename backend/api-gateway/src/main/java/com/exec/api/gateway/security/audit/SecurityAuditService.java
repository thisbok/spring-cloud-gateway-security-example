package com.exec.api.gateway.security.audit;

import com.exec.api.gateway.security.SecurityLayerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 보안 감사 서비스
 * <p>
 * 모든 보안 관련 이벤트를 Kafka 로 전송하여 중앙 집중식 보안 로깅 시스템을 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SecurityLayerConfig securityConfig;

    /**
     * 보안 감사 이벤트를 비동기로 Kafka 에 전송
     *
     * @param event 보안 감사 이벤트
     * @return Mono<Void>
     */
    public Mono<Void> publishSecurityEvent(SecurityAuditEvent event) {
        if (!securityConfig.getMonitoring().getAuditLogging().isEnabled()) {
            return Mono.empty();
        }

        return Mono.fromRunnable(() -> sendToKafka(event))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(throwable -> log.error("Failed to publish security audit event: {}", event.getRequestId(), throwable))
                .onErrorResume(throwable -> {
                    // Kafka 전송 실패 시 로컬 로그로 대체
                    logSecurityEventLocally(event, throwable);
                    return Mono.empty();
                })
                .then();
    }

    /**
     * API 키 검증 성공 이벤트 발행
     */
    public Mono<Void> publishApiKeyValidationSuccess(String requestId, String accessKey, String ipAddress) {
        SecurityAuditEvent event = SecurityAuditEvent.apiKeyValidationSuccess(requestId, accessKey, ipAddress);
        return publishSecurityEvent(event);
    }

    /**
     * API 키 검증 실패 이벤트 발행
     */
    public Mono<Void> publishApiKeyValidationFailure(String requestId, String accessKey, String ipAddress, String reason) {
        SecurityAuditEvent event = SecurityAuditEvent.apiKeyValidationFailure(requestId, accessKey, ipAddress, reason);
        return publishSecurityEvent(event);
    }

    /**
     * Rate Limit 초과 이벤트 발행
     */
    public Mono<Void> publishRateLimitExceeded(String requestId, String accessKey, String ipAddress, String limitType) {
        SecurityAuditEvent event = SecurityAuditEvent.rateLimitExceeded(requestId, accessKey, ipAddress, limitType);
        return publishSecurityEvent(event);
    }

    /**
     * DDoS 공격 탐지 이벤트 발행
     */
    public Mono<Void> publishDDoSAttackDetected(String requestId, String ipAddress, int requestCount) {
        SecurityAuditEvent event = SecurityAuditEvent.ddosAttackDetected(requestId, ipAddress, requestCount);
        return publishSecurityEvent(event);
    }

    /**
     * 보안 공격 시도 이벤트 발행
     */
    public Mono<Void> publishSecurityAttackAttempt(String requestId, SecurityAuditEvent.EventType attackType,
                                                   String ipAddress, String uri, String payload) {
        SecurityAuditEvent event = SecurityAuditEvent.securityAttackAttempt(requestId, attackType, ipAddress, uri, payload);
        return publishSecurityEvent(event);
    }

    /**
     * 배치로 여러 이벤트 발행
     */
    public Mono<Void> publishSecurityEventsBatch(SecurityAuditEvent... events) {
        return Mono.fromRunnable(() -> {
                    for (SecurityAuditEvent event : events) {
                        sendToKafka(event);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(throwable -> log.error("Failed to publish security audit events batch", throwable))
                .onErrorResume(throwable -> {
                    // 배치 전송 실패 시 개별적으로 로컬 로그 처리
                    for (SecurityAuditEvent event : events) {
                        logSecurityEventLocally(event, throwable);
                    }
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Kafka 로 이벤트 전송
     */
    private void sendToKafka(SecurityAuditEvent event) {
        try {
            String topic = securityConfig.getMonitoring().getAuditLogging().getKafkaTopic();
            String key = generatePartitionKey(event);

            kafkaTemplate.send(topic, key, event)
                    .whenComplete((result, failure) -> {
                        if (failure == null) {
                            log.debug("Security audit event sent successfully: {}", event.getRequestId());
                        } else {
                            log.error("Failed to send security audit event: {}", event.getRequestId(), failure);
                        }
                    });

        } catch (Exception e) {
            log.error("Error sending security audit event to Kafka: {}", event.getRequestId(), e);
            throw e;
        }
    }

    /**
     * 파티션 키 생성 (accessKey 중심)
     * <p>
     * - accessKey 존재: accessKey 사용 (같은 API Key 의 모든 이벤트 순서 보장)
     * - accessKey 없음: IP 기반 추적 (인증 전 공격 탐지)
     */
    private String generatePartitionKey(SecurityAuditEvent event) {
        // accessKey 우선 (API Key 기반 추적)
        if (event.getAccessKey() != null) {
            return event.getAccessKey();
        }

        // Fallback: IP 주소 (인증 전 공격 시도 추적)
        if (event.getIpAddress() != null) {
            return "ip-" + event.getIpAddress();
        }

        // 최후 수단
        return "unknown";
    }


    /**
     * Kafka 전송 실패 시 로컬 로그로 대체
     */
    private void logSecurityEventLocally(SecurityAuditEvent event, Throwable error) {
        String logLevel = event.getEventType().getLevel().name();
        String message = String.format(
                "SECURITY_AUDIT [%s] %s - %s | RequestID: %s | AccessKey: %s | IP: %s | URI: %s | Error: %s",
                logLevel,
                event.getEventType().name(),
                event.getDescription(),
                event.getRequestId(),
                event.getAccessKey(),
                event.getIpAddress(),
                event.getUri(),
                error != null ? error.getMessage() : "None"
        );

        // 이벤트 레벨에 따라 적절한 로그 레벨로 출력
        switch (event.getEventType().getLevel()) {
            case CRITICAL:
            case SECURITY:
            case ERROR:
                log.error(message);
                break;
            case WARNING:
                log.warn(message);
                break;
            case INFO:
            default:
                log.info(message);
                break;
        }

        // 추가적으로 위험도가 높은 이벤트는 별도 처리
        if (event.getEventType().getRiskScore() != null && event.getEventType().getRiskScore() >= 7.0) {
            log.error("HIGH_RISK_SECURITY_EVENT: {} | RiskScore: {} | Details: {}",
                    event.getEventType(), event.getEventType().getRiskScore(), event.getAdditionalData());
        }
    }
}