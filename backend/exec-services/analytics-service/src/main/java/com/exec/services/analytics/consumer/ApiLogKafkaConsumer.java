package com.exec.services.analytics.consumer;

import com.exec.services.analytics.service.ApiGatewayLogService;
import com.exec.services.analytics.service.RealTimeAnalyticsService;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 🔄 API 로그 Kafka Consumer
 * <p>
 * Kafka 에서 API 로그 스트림을 소비하여 Elasticsearch 에 저장하고
 * 실시간 집계 처리를 수행합니다.
 * <p>
 * 장점:
 * 1. 메인 API 처리와 로그 저장 완전 분리
 * 2. 장애 시 재처리 가능
 * 3. 배치 처리로 Elasticsearch 성능 최적화
 * 4. 실시간 스트림 분석 가능
 * 5. DLQ 패턴으로 실패 메시지 추적 및 재처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiLogKafkaConsumer {

    // DLQ Topic 이름
    private static final String DLQ_TOPIC = "api-gateway-fail-logs";
    // 최대 재시도 횟수
    private static final int MAX_RETRY_COUNT = 3;
    private final ApiGatewayLogService apiGatewayLogService;
    private final RealTimeAnalyticsService realTimeAnalyticsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * API 호출 로그 일괄 처리 Consumer
     * <p>
     * 특징:
     * - 배치 처리로 Elasticsearch 성능 최적화
     * - 수동 커밋으로 장애 시 재처리 보장
     * - DLQ(Dead Letter Queue) 지원
     */
    @KafkaListener(
            topics = "api-gateway-request-logs",
            groupId = "api-log-elasticsearch-group",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeApiCallLogsBatch(
            @Payload List<ApiGatewayRequestLogDto> logDtos,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {

        try {
            log.info("배치 처리 시작: {} 건의 API 로그", logDtos.size());

            // 1. Elasticsearch 배치 저장
            bulkSaveToElasticsearch(logDtos);

            // 2. 실시간 집계 업데이트
            updateRealTimeAggregates(logDtos);

            // 3. 수동 커밋 (모든 처리 완료 후)
            acknowledgment.acknowledge();

            log.info("배치 처리 완료: {} 건", logDtos.size());

        } catch (Exception e) {
            log.error("배치 처리 실패: {} 건", logDtos.size(), e);
            // DLQ 로 전송하거나 재시도 로직 구현
            handleBatchProcessingError(logDtos, e);
        }
    }

    /**
     * 실시간 분석용 Consumer (별도 그룹)
     * <p>
     * 에러 처리 전략:
     * 1. 일시적 오류 (네트워크, DB): 재시도 (offset 커밋 안함)
     * 2. 영구적 오류 (데이터 형식): DLQ 전송 후 커밋
     * 3. 분석 실패는 치명적이지 않으므로 로깅 후 커밋
     */
    @KafkaListener(
            topics = "api-gateway-request-logs",
            groupId = "api-log-realtime-group",
            concurrency = "3"
    )
    public void consumeForRealTimeAnalysis(
            @Payload ApiGatewayRequestLogDto logDto,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("실시간 분석 처리: {}", logDto.getRequestId());

            // 실시간 메트릭 업데이트
            realTimeAnalyticsService.processApiCallLog(logDto);

            // 이상 징후 탐지
            detectAnomalies(logDto);

            // 알림 트리거 확인
            checkAlertConditions(logDto);

            // 성공: offset 커밋
        } catch (Exception e) {
            // 에러 처리: 분석 실패는 치명적이지 않으므로 커밋
            // 에러 로깅 (상세 정보 포함)
            String errorType = classifyError(e);

            log.error("[실시간 분석 실패] requestId={}, errorType={}, message={}",
                    logDto.getRequestId(),
                    errorType,
                    e.getMessage(),
                    e);
        }
        acknowledgment.acknowledge();
    }

    /**
     * 에러 로그 전용 Consumer
     */
    @KafkaListener(
            topics = "api-errors",
            groupId = "api-error-monitoring-group"
    )
    public void consumeErrorLogs(
            @Payload ApiGatewayRequestLogDto errorLogDto,
            Acknowledgment acknowledgment) {

        try {
            log.warn("에러 로그 처리: {} - {}",
                    errorLogDto.getRequestId(),
                    errorLogDto.getStatusCode());

            // 에러 패턴 분석
            analyzeErrorPattern(errorLogDto);

            // 긴급 알림 발송
            sendUrgentAlert(errorLogDto);

            // Elasticsearch 에러 인덱스에 저장
            saveToErrorIndex(errorLogDto);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("에러 로그 처리 실패: {}", errorLogDto.getRequestId(), e);
        }
    }

    /**
     * Elasticsearch 배치 저장
     */
    private void bulkSaveToElasticsearch(List<ApiGatewayRequestLogDto> logDtos) {
        try {
            // Spring Data Elasticsearch 를 사용한 배치 저장
            apiGatewayLogService.saveLogsBatch(logDtos);

            log.debug("Elasticsearch 배치 저장 완료: {} 건", logDtos.size());

        } catch (Exception e) {
            log.error("Elasticsearch 배치 저장 실패", e);
            throw new RuntimeException("Elasticsearch 저장 실패", e);
        }
    }

    /**
     * 실시간 집계 업데이트
     */
    private void updateRealTimeAggregates(List<ApiGatewayRequestLogDto> logDtos) {
        try {
            for (ApiGatewayRequestLogDto logDto : logDtos) {
                // Redis 기반 실시간 카운터 업데이트
                realTimeAnalyticsService.incrementCounters(logDto);

                // 슬라이딩 윈도우 메트릭 업데이트
                realTimeAnalyticsService.updateSlidingWindow(logDto);
            }

        } catch (Exception e) {
            log.error("실시간 집계 업데이트 실패", e);
            // 집계 실패는 치명적이지 않으므로 예외를 다시 던지지 않음
        }
    }

    /**
     * 이상 징후 탐지
     */
    private void detectAnomalies(ApiGatewayRequestLogDto logDto) {
        try {
            // 응답 시간 이상
            if (logDto.getResponseTimeMs() != null && logDto.getResponseTimeMs() > 5000) {
                log.warn("응답 시간 이상 탐지: {}ms - {}",
                        logDto.getResponseTimeMs(), logDto.getRequestId());
            }

            // accessKey 기반 모니터링 (인증 실패 시에도 추적 가능)
            String accessKey = logDto.getAccessKey();
            if (accessKey == null) {
                log.warn("AccessKey 누락으로 이상 징후 탐지 건너뜀: {}", logDto.getRequestId());
                return;
            }

            // 에러율 급증
            if (logDto.getHasError()) {
                realTimeAnalyticsService.checkErrorRateSpike(accessKey);
            }

            // 트래픽 급증
            realTimeAnalyticsService.checkTrafficSpike(accessKey);

        } catch (Exception e) {
            log.error("이상 징후 탐지 실패: {}", logDto.getRequestId(), e);
        }
    }

    /**
     * 알림 조건 확인
     */
    private void checkAlertConditions(ApiGatewayRequestLogDto logDto) {
        // 5xx 에러 알림
        if (logDto.getStatusCode() != null && logDto.getStatusCode() >= 500) {
            sendServerErrorAlert(logDto);
        }

        // API 키 제한 초과
        if (logDto.getStatusCode() != null && logDto.getStatusCode() == 429) {
            sendRateLimitAlert(logDto);
        }

        // 보안 위험 탐지
        if ("HIGH".equals(logDto.getSecurityRiskLevel())) {
            sendSecurityAlert(logDto);
        }
    }

    /**
     * 에러 패턴 분석
     */
    private void analyzeErrorPattern(ApiGatewayRequestLogDto errorLogDto) {
        // TODO: 에러 패턴 분석 로직 구현
        log.info("에러 패턴 분석: {}", errorLogDto.getErrorMessage());
    }

    /**
     * 긴급 알림 발송
     */
    private void sendUrgentAlert(ApiGatewayRequestLogDto errorLogDto) {
        // TODO: 긴급 알림 발송 로직 구현
        log.warn("긴급 알림 발송: {}", errorLogDto.getRequestId());
    }

    /**
     * 에러 인덱스 저장
     */
    private void saveToErrorIndex(ApiGatewayRequestLogDto errorLogDto) {
        try {
            // 일반 로그 인덱스에 저장 (에러도 동일한 인덱스 사용)
            apiGatewayLogService.saveLog(errorLogDto);
        } catch (Exception e) {
            log.error("에러 인덱스 저장 실패: {}", errorLogDto.getRequestId(), e);
        }
    }

    /**
     * 서버 에러 알림
     */
    private void sendServerErrorAlert(ApiGatewayRequestLogDto logDto) {
        log.error("서버 에러 알림: {} {} - Status: {}",
                logDto.getMethod(), logDto.getUri(), logDto.getStatusCode());
    }

    /**
     * Rate Limit 알림
     */
    private void sendRateLimitAlert(ApiGatewayRequestLogDto logDto) {
        log.warn("Rate Limit 초과 알림: AccessKey {} (Client: {}) - {}",
                logDto.getAccessKey(), logDto.getClientId(), logDto.getRequestId());
    }

    /**
     * 보안 알림
     */
    private void sendSecurityAlert(ApiGatewayRequestLogDto logDto) {
        log.error("보안 위험 탐지: {} - {}",
                logDto.getRequestId(), logDto.getAttackType());
    }

    /**
     * 에러 유형 분류
     */
    private String classifyError(Exception error) {
        if (error instanceof org.springframework.data.redis.RedisConnectionFailureException) {
            return "REDIS_CONNECTION_FAILURE";
        } else if (error instanceof java.net.ConnectException) {
            return "NETWORK_ERROR";
        } else if (error instanceof NullPointerException) {
            return "NULL_POINTER";
        } else if (error instanceof IllegalArgumentException) {
            return "INVALID_DATA";
        } else {
            return "UNKNOWN_ERROR";
        }
    }

    // ==================== DLQ 전송 ====================

    /**
     * Dead Letter Queue 로 메시지 전송
     * <p>
     * 실패한 메시지를 별도 Kafka Topic 으로 전송하여:
     * 1. 메시지 유실 방지
     * 2. 추후 재처리 가능
     * 3. 에러 패턴 분석
     * 4. 모니터링 및 알림
     */
    private void sendToDeadLetterQueue(
            ApiGatewayRequestLogDto originalMessage,
            Exception error,
            int retryCount) {

        try {
            // DLQ 메시지 생성 (메타데이터 포함)
            FailedLogMessage failedMessage = FailedLogMessage.builder()
                    .originalMessage(originalMessage)
                    .errorType(classifyError(error))
                    .errorMessage(error.getMessage())
                    .stackTrace(getStackTrace(error))
                    .retryCount(retryCount)
                    .failedAt(LocalDateTime.now())
                    .requestId(originalMessage.getRequestId())
                    .clientId(originalMessage.getClientId())
                    .accessKey(originalMessage.getAccessKey())
                    .build();

            // DLQ Topic 으로 전송
            kafkaTemplate.send(DLQ_TOPIC, originalMessage.getRequestId(), failedMessage)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("DLQ 전송 실패: requestId={}", originalMessage.getRequestId(), ex);
                            // DLQ 전송도 실패한 경우 로컬 파일에 저장 (최후의 수단)
                            saveToLocalFile(failedMessage);
                        } else {
                            log.info("DLQ 전송 성공: requestId={}, retryCount={}",
                                    originalMessage.getRequestId(), retryCount);
                        }
                    });

        } catch (Exception e) {
            log.error("DLQ 전송 중 예외 발생: requestId={}",
                    originalMessage.getRequestId(), e);
            saveToLocalFile(FailedLogMessage.builder()
                    .originalMessage(originalMessage)
                    .errorMessage(error.getMessage())
                    .build());
        }
    }

    /**
     * 스택 트레이스를 문자열로 변환
     */
    private String getStackTrace(Exception error) {
        if (error == null) return null;

        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        String stackTrace = sw.toString();

        // 너무 길면 잘라냄 (Kafka 메시지 크기 제한 고려)
        return stackTrace.length() > 5000
                ? stackTrace.substring(0, 5000) + "\n... (truncated)"
                : stackTrace;
    }

    /**
     * DLQ 전송도 실패한 경우 로컬 파일에 저장 (최후의 수단)
     */
    private void saveToLocalFile(FailedLogMessage failedMessage) {
        try {
            // TODO: 실제 파일 저장 로직 구현
            log.warn("로컬 파일 저장 필요: requestId={}", failedMessage.getRequestId());
            // 예: /var/log/analytics/failed-messages/{date}/{requestId}.json
        } catch (Exception e) {
            log.error("로컬 파일 저장도 실패", e);
        }
    }

    /**
     * 배치 처리 에러 핸들링
     * <p>
     * 배치 처리는 핵심 기능이므로 더 신중한 처리 필요
     */
    private void handleBatchProcessingError(List<ApiGatewayRequestLogDto> logDtos, Exception error) {
        String errorType = classifyError(error);

        log.error("[배치 처리 실패] count={}, errorType={}, message={}",
                logDtos.size(),
                errorType,
                error.getMessage(),
                error);

        // 배치 처리 실패 시 전략
        if (isRetryableError(error)) {
            // 일시적 오류: offset 커밋하지 않고 재시도 유도
            log.warn("재시도 가능한 에러 - offset 커밋 생략: {}", errorType);
            // acknowledgment.acknowledge() 를 호출하지 않음 → 재시도됨

        } else {
            // 영구적 오류: 개별 처리 시도
            log.error("영구적 에러 - 개별 처리 시도: {}", errorType);
            processBatchIndividually(logDtos);

            // DLQ 전송 (실패한 항목만)
            // sendToDeadLetterQueue(logDtos, error);
        }
    }

    /**
     * 재시도 가능한 에러 여부 판단
     */
    private boolean isRetryableError(Exception error) {
        // 네트워크 오류, DB 연결 오류 등은 재시도 가능
        return error instanceof org.springframework.dao.TransientDataAccessException
                || error instanceof java.net.ConnectException
                || error instanceof org.springframework.data.redis.RedisConnectionFailureException;
    }

    /**
     * 배치를 개별 항목으로 처리
     */
    private void processBatchIndividually(List<ApiGatewayRequestLogDto> logDtos) {
        log.info("배치 개별 처리 시작: {} 건", logDtos.size());

        int successCount = 0;
        int failureCount = 0;

        for (ApiGatewayRequestLogDto logDto : logDtos) {
            try {
                apiGatewayLogService.saveLog(logDto);
                successCount++;
            } catch (Exception e) {
                log.error("개별 항목 처리 실패: requestId={}", logDto.getRequestId(), e);
                // 개별 실패는 DLQ 로 전송
                sendToDeadLetterQueue(logDto, e, 1);
                failureCount++;
            }
        }

        log.info("배치 개별 처리 완료: 성공={}, 실패={}", successCount, failureCount);
    }

    // ==================== DLQ Consumer ====================

    /**
     * DLQ Consumer: 실패한 메시지 재처리
     * <p>
     * 실무 운영 시나리오:
     * 1. DLQ 메시지 모니터링 (알림 발송)
     * 2. 수동 확인 및 조치
     * 3. 재처리 가능 여부 판단
     * 4. 재처리 또는 영구 보관
     */
    @KafkaListener(
            topics = DLQ_TOPIC,
            groupId = "api-gateway-fail-log-group",
            concurrency = "1"  // DLQ 는 단일 스레드로 천천히 처리
    )
    public void consumeFailedLogs(
            @Payload FailedLogMessage failedMessage,
            Acknowledgment acknowledgment) {

        try {
            log.warn("DLQ 메시지 수신: requestId={}, errorType={}, retryCount={}",
                    failedMessage.getRequestId(),
                    failedMessage.getErrorType(),
                    failedMessage.getRetryCount());

            // 재시도 횟수 체크
            if (failedMessage.getRetryCount() >= MAX_RETRY_COUNT) {
                log.error("최대 재시도 횟수 초과: requestId={}, retryCount={}",
                        failedMessage.getRequestId(), failedMessage.getRetryCount());

                // 영구 실패 처리 (DB 에 저장, 알림 발송 등)
                handlePermanentFailure(failedMessage);

                // offset 커밋 (더 이상 재시도 안함)
                acknowledgment.acknowledge();
                return;
            }

            // 재처리 시도
            boolean retrySuccess = retryFailedMessage(failedMessage);

            if (retrySuccess) {
                log.info("DLQ 메시지 재처리 성공: requestId={}", failedMessage.getRequestId());
                acknowledgment.acknowledge();
            } else {
                log.warn("DLQ 메시지 재처리 실패: requestId={}, 재시도 카운트 증가",
                        failedMessage.getRequestId());

                // 재시도 카운트 증가 후 다시 DLQ 로 전송
                sendToDeadLetterQueue(
                        failedMessage.getOriginalMessage(),
                        new RuntimeException(failedMessage.getErrorMessage()),
                        failedMessage.getRetryCount() + 1
                );

                // offset 커밋 (다음 재시도는 새로운 DLQ 메시지로)
                acknowledgment.acknowledge();
            }

        } catch (Exception e) {
            log.error("DLQ Consumer 처리 실패: requestId={}",
                    failedMessage.getRequestId(), e);
            // 치명적 에러: offset 커밋 안함 (재시도됨)
        }
    }

    /**
     * 실패한 메시지 재처리 시도
     */
    private boolean retryFailedMessage(FailedLogMessage failedMessage) {
        try {
            // 원본 메시지를 다시 Elasticsearch 에 저장 시도
            apiGatewayLogService.saveLog(failedMessage.getOriginalMessage());

            log.info("재처리 성공: requestId={}", failedMessage.getRequestId());
            return true;

        } catch (Exception e) {
            log.warn("재처리 실패: requestId={}, error={}",
                    failedMessage.getRequestId(), e.getMessage());
            return false;
        }
    }

    /**
     * 영구 실패 처리
     */
    private void handlePermanentFailure(FailedLogMessage failedMessage) {
        log.error("🚨 영구 실패 메시지: requestId={}, errorType={}",
                failedMessage.getRequestId(),
                failedMessage.getErrorType());

        // TODO: 다음 작업 수행
        // 1. DB 에 영구 실패 레코드 저장
        // 2. 관리자에게 알림 발송 (Slack, Email)
        // 3. 모니터링 대시보드에 표시
        // 4. 수동 처리 대기 큐에 추가
    }

    // ==================== DTO ====================

    /**
     * 실패 메시지 DTO
     */
    @lombok.Builder
    @lombok.Getter
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class FailedLogMessage {
        private ApiGatewayRequestLogDto originalMessage;  // 원본 메시지
        private String errorType;                         // 에러 유형
        private String errorMessage;                      // 에러 메시지
        private String stackTrace;                        // 스택 트레이스
        private int retryCount;                           // 재시도 횟수
        private LocalDateTime failedAt;                   // 실패 시각
        private String requestId;                         // 추적용
        private String clientId;                          // 추적용
        private String accessKey;                         // 추적용
    }
}