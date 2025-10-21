package com.exec.api.gateway.service;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class IdempotencyService {

    // Redis 키 프리픽스
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    @Value("${security.layers.authentication.api-key.idempotency.enabled:true}")
    private boolean idempotencyEnabled;
    @Value("${security.layers.authentication.api-key.idempotency.ttl-hours:24}")
    private long idempotencyTtlHours;

    public IdempotencyService(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    /**
     * Idempotency Key 검증
     * 한 번 사용된 키는 다시 사용할 수 없음 (중복 요청 방지)
     *
     * @param accessKey      Access Key (API Key)
     * @param idempotencyKey Idempotency Key
     * @return 처리 가능 여부 (true: 신규 요청, false: 중복 요청)
     */
    public Mono<IdempotencyResult> checkAndMarkProcessing(
            String accessKey,
            String idempotencyKey) {

        // Idempotency 가 비활성화된 경우 항상 허용
        if (!idempotencyEnabled) {
            log.debug("Idempotency check is disabled, allowing request");
            return Mono.just(IdempotencyResult.allowed());
        }

        String redisKey = buildRedisKey(accessKey, idempotencyKey);
        // setIfAbsent 를 사용하여 원자적으로 처리
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, createInitialRecord(accessKey, idempotencyKey), Duration.ofMinutes(10))
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // 신규 요청 - 키가 성공적으로 설정됨
                        log.debug("New idempotency key registered: accessKey={}, key={}", accessKey, idempotencyKey);
                        return IdempotencyResult.allowed();
                    } else {
                        // 중복 요청 - 키가 이미 존재함
                        log.info("Duplicate request detected: accessKey={}, key={}", accessKey, idempotencyKey);
                        return IdempotencyResult.duplicate();
                    }
                })
                .doOnError(error ->
                        log.error("Idempotency check failed: accessKey={}, key={}", accessKey, idempotencyKey, error));
    }

    /**
     * 초기 레코드 생성
     */
    private IdempotencyRecord createInitialRecord(String accessKey, String idempotencyKey) {
        return IdempotencyRecord.builder()
                .status("PENDING")  // 초기 상태
                .accessKey(accessKey)
                .idempotencyKey(idempotencyKey)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 처리 완료 상태로 마킹
     *
     * @param accessKey      Access Key (API Key)
     * @param idempotencyKey Idempotency Key
     * @param response       응답 데이터
     * @return 완료 처리 결과
     */
    public Mono<Void> markAsCompleted(String accessKey, String idempotencyKey, String response) {
        String redisKey = buildRedisKey(accessKey, idempotencyKey);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .status("COMPLETED")
                .accessKey(accessKey)
                .idempotencyKey(idempotencyKey)
                .response(response)
                .timestamp(System.currentTimeMillis())
                .build();

        Duration ttl = Duration.ofHours(idempotencyTtlHours);
        return reactiveRedisTemplate.opsForValue()
                .set(redisKey, record, ttl)
                .doOnSuccess(result ->
                        log.debug("Marked as completed: accessKey={}, key={}", accessKey, idempotencyKey))
                .doOnError(error ->
                        log.error("Failed to mark as completed: accessKey={}, key={}",
                                accessKey, idempotencyKey, error))
                .then();
    }


    /**
     * Idempotency Key 유효성 검증
     *
     * @param idempotencyKey 검증할 키
     * @return 유효성 여부
     */
    public Mono<Boolean> validateIdempotencyKey(String idempotencyKey) {
        return Mono.fromCallable(() -> {
            if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
                return false;
            }

            // UUID 형식 검증
            try {
                UUID.fromString(idempotencyKey);
                return true;
            } catch (IllegalArgumentException e) {
                // UUID 가 아닌 경우 길이 및 문자 검증
                if (idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
                    log.warn("Invalid idempotency key length: {}", idempotencyKey.length());
                    return false;
                }

                // 알파벳, 숫자, 하이픈, 언더스코어만 허용
                if (!idempotencyKey.matches("^[a-zA-Z0-9\\-_]+$")) {
                    log.warn("Invalid idempotency key format: {}", idempotencyKey);
                    return false;
                }

                return true;
            }
        });
    }

    /**
     * Redis 키 생성
     */
    private String buildRedisKey(String accessKey, String idempotencyKey) {
        return IDEMPOTENCY_PREFIX + accessKey + ":" + idempotencyKey;
    }

    /**
     * Idempotency 검증 결과
     */
    public static class IdempotencyResult {
        private final boolean allowed;

        private IdempotencyResult(boolean allowed) {
            this.allowed = allowed;
        }

        public static IdempotencyResult allowed() {
            return new IdempotencyResult(true);
        }

        public static IdempotencyResult duplicate() {
            return new IdempotencyResult(false);
        }

        public boolean isAllowed() {
            return allowed;
        }
    }

    /**
     * Redis 에 저장할 Idempotency 레코드
     */
    @Builder
    @Getter
    @Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class IdempotencyRecord implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        private String status;        // PROCESSING, COMPLETED
        private String accessKey;     // Access Key (API Key)
        private String idempotencyKey;
        private String response;      // 완료된 경우 응답 데이터
        private long timestamp;
    }
}