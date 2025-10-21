package com.exec.api.gateway.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 🚀 API 호출 로그 이벤트 발행 서비스
 * <p>
 * API 호출 로그를 Kafka 이벤트 스트림으로 발행:
 * 1. Kafka: 이벤트 소싱 패턴으로 로그 스트림 발행
 * 2. 실제 저장/분석은 Analytics Service 에서 처리
 * <p>
 * Gateway 는 라우팅과 기본 로깅에만 집중하고,
 * 복잡한 분석 로직은 Analytics Service 로 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiCallLogService {

    // Kafka Topic 상수
    private static final String API_CALL_LOGS_TOPIC = "api-gateway-request-logs";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * API 호출 로그를 Kafka 이벤트 스트림으로 발행
     *
     * @param logDto API 호출 로그 데이터
     */
    @Async("apiLoggingExecutor")
    public CompletableFuture<Void> saveApiCallLogAsync(ApiGatewayRequestLogDto logDto) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Kafka 이벤트 발행 (Event Sourcing 패턴)
                // 파티션 키 생성 (클라이언트별 순서 보장)
                String partitionKey = generatePartitionKey(logDto);

                // 메인 로그 토픽에 전송 (에러/정상 모두 포함)
                kafkaTemplate.send(API_CALL_LOGS_TOPIC, partitionKey, logDto);

                log.debug("Kafka 전송 완료 for AccessKey: {}", logDto.getAccessKey());
            } catch (Exception e) {
                log.error("Failed to publish API call log event for AccessKey: {}", logDto.getAccessKey(), e);
                // fallback: 로컬 파일 로그로 백업
                fallbackToLocalLog(logDto, e);
            }
        });
    }

    /**
     * Kafka 파티션 키 생성
     * <p>
     * accessKey 를 우선적으로 사용하여:
     * - 인증 실패 요청도 추적 가능 (accessKey 는 실패 시에도 존재)
     * - 보안 모니터링 강화 (Brute Force Attack, API Key Leak 탐지)
     * - 더 균등한 데이터 분산 (API Key 수 > Client 수)
     */
    private String generatePartitionKey(ApiGatewayRequestLogDto logDto) {
        // 1 순위: accessKey (성공/실패 모두 추적)
        if (logDto.getAccessKey() != null) {
            return logDto.getAccessKey();
        }
        // 2 순위: clientId (accessKey 가 없는 경우)
        if (logDto.getClientId() != null) {
            return logDto.getClientId();
        }
        // 3 순위: apiKeyId (레거시 호환)
        if (logDto.getApiKeyId() != null) {
            return "api-key-" + logDto.getApiKeyId();
        }
        // 4 순위: IP (인증 정보가 전혀 없는 경우)
        if (logDto.getClientIp() != null) {
            return "ip-" + logDto.getClientIp();
        }
        return "unknown";
    }

    /**
     * 저장 실패 시 로컬 파일로 백업
     */
    private void fallbackToLocalLog(ApiGatewayRequestLogDto logDto, Exception error) {
        try {
            String logJson = objectMapper.writeValueAsString(logDto);
            log.error("API_CALL_LOG_FALLBACK: {} | ERROR: {}", logJson, error.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fallback log for AccessKey: {}", logDto.getAccessKey(), e);
        }
    }
}