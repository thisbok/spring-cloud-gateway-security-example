package com.exec.services.analytics.service;

import com.exec.services.analytics.domain.ApiGatewayRequestLog;
import com.exec.services.analytics.repository.ApiGatewayRequestLogRepository;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API Gateway 로그 처리 서비스
 * <p>
 * Kafka 로부터 수신한 로그를 Elasticsearch 에 저장하고,
 * 저장된 로그를 조회하는 기능을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiGatewayLogService {

    private final ApiGatewayRequestLogRepository repository;

    /**
     * API Gateway 로그 저장
     * <p>
     * DTO 를 Elasticsearch Document 로 변환하여 저장합니다.
     * 필요한 경우 해당 날짜의 인덱스를 자동으로 생성합니다.
     *
     * @param dto API Gateway 요청 로그 DTO
     * @return 저장된 Document
     */
    public ApiGatewayRequestLog saveLog(ApiGatewayRequestLogDto dto) {
        try {
            // DTO → Document 변환
            ApiGatewayRequestLog document = ApiGatewayRequestLog.from(dto);

            // Elasticsearch 저장
            ApiGatewayRequestLog saved = repository.save(document);

            log.debug("API Gateway log saved successfully: requestId={}, clientId={}",
                    dto.getRequestId(), dto.getClientId());

            return saved;
        } catch (Exception e) {
            log.error("Failed to save API Gateway log: requestId={}, error={}",
                    dto.getRequestId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save log to Elasticsearch", e);
        }
    }

    /**
     * 로그 일괄 저장
     *
     * @param dtoList 로그 DTO 리스트
     * @return 저장된 Document 리스트
     */
    public List<ApiGatewayRequestLog> saveLogsBatch(List<ApiGatewayRequestLogDto> dtoList) {
        try {
            log.info("Batch saving {} API Gateway logs", dtoList.size());

            // DTO → Document 변환
            List<ApiGatewayRequestLog> documents = dtoList.stream()
                    .map(ApiGatewayRequestLog::from)
                    .collect(Collectors.toList());

            // 일괄 저장
            Iterable<ApiGatewayRequestLog> saved = repository.saveAll(documents);

            log.info("Successfully saved {} logs to Elasticsearch", dtoList.size());

            return (List<ApiGatewayRequestLog>) saved;
        } catch (Exception e) {
            log.error("Failed to batch save API Gateway logs: count={}, error={}",
                    dtoList.size(), e.getMessage(), e);
            throw new RuntimeException("Failed to batch save logs to Elasticsearch", e);
        }
    }

    /**
     * RequestId 로 로그 조회
     */
    public Optional<ApiGatewayRequestLogDto> findByRequestId(String requestId) {
        return repository.findById(requestId)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * ClientId 로 로그 조회 (페이징)
     */
    public Page<ApiGatewayRequestLogDto> findByClientId(String clientId, Pageable pageable) {
        return repository.findByClientId(clientId, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * AccessKey 로 로그 조회 (페이징)
     */
    public Page<ApiGatewayRequestLogDto> findByAccessKey(String accessKey, Pageable pageable) {
        return repository.findByAccessKey(accessKey, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 특정 기간의 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByTimestampBetween(
            String startTime, String endTime, Pageable pageable) {
        return repository.findByTimestampBetween(startTime, endTime, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 에러 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findErrorLogs(Pageable pageable) {
        return repository.findByHasError(true, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 특정 상태코드 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByStatusCode(Integer statusCode, Pageable pageable) {
        return repository.findByStatusCode(statusCode, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * ClientId 와 기간으로 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByClientIdAndTimestampBetween(
            String clientId, String startTime, String endTime, Pageable pageable) {
        return repository.findByClientIdAndTimestampBetween(clientId, startTime, endTime, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 느린 응답 로그 조회 (3 초 이상)
     */
    public Page<ApiGatewayRequestLogDto> findSlowRequests(Pageable pageable) {
        return repository.findSlowRequests(pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 엔드포인트별 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByEndpoint(String endpoint, Pageable pageable) {
        return repository.findByEndpoint(endpoint, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 보안 위험 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findSecurityRiskLogs(Pageable pageable) {
        return repository.findBySecurityRiskLevelIsNotNull(pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 공격 유형별 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByAttackType(String attackType, Pageable pageable) {
        return repository.findByAttackType(attackType, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * ClientIp 로 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByClientIp(String clientIp, Pageable pageable) {
        return repository.findByClientIp(clientIp, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * HTTP 메서드별 로그 조회
     */
    public Page<ApiGatewayRequestLogDto> findByMethod(String method, Pageable pageable) {
        return repository.findByMethod(method, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * URI 패턴 검색
     */
    public Page<ApiGatewayRequestLogDto> searchByUriPattern(String uriPattern, Pageable pageable) {
        return repository.searchByUriPattern(uriPattern, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 에러 메시지 검색
     */
    public Page<ApiGatewayRequestLogDto> searchByErrorMessage(String errorMessage, Pageable pageable) {
        return repository.searchByErrorMessage(errorMessage, pageable)
                .map(ApiGatewayRequestLog::toDto);
    }

    /**
     * 특정 날짜 로그 카운트
     */
    public long countByDate(String date) {
        return repository.countByDate(date);
    }

    /**
     * ClientId 별 로그 카운트
     */
    public long countByClientId(String clientId) {
        return repository.countByClientId(clientId);
    }

    /**
     * 상태코드별 로그 카운트
     */
    public long countByStatusCode(Integer statusCode) {
        return repository.countByStatusCode(statusCode);
    }

    /**
     * 로그 삭제
     */
    public void deleteLog(String requestId) {
        repository.deleteById(requestId);
        log.info("API Gateway log deleted: requestId={}", requestId);
    }
}
