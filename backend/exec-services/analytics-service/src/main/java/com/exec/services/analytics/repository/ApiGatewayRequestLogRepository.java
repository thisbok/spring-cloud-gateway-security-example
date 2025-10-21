package com.exec.services.analytics.repository;

import com.exec.services.analytics.domain.ApiGatewayRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * API Gateway 요청 로그 Elasticsearch Repository
 * <p>
 * Spring Data Elasticsearch 를 사용한 검색 및 집계 기능 제공
 */
@Repository
public interface ApiGatewayRequestLogRepository extends ElasticsearchRepository<ApiGatewayRequestLog, String> {

    /**
     * ClientId 로 로그 검색
     */
    Page<ApiGatewayRequestLog> findByClientId(String clientId, Pageable pageable);

    /**
     * AccessKey 로 로그 검색
     */
    Page<ApiGatewayRequestLog> findByAccessKey(String accessKey, Pageable pageable);

    /**
     * 특정 기간의 로그 검색
     */
    @Query("{\"range\": {\"timestamp\": {\"gte\": \"?0\", \"lte\": \"?1\"}}}")
    Page<ApiGatewayRequestLog> findByTimestampBetween(String startTime, String endTime, Pageable pageable);

    /**
     * 에러가 있는 로그 검색
     */
    Page<ApiGatewayRequestLog> findByHasError(Boolean hasError, Pageable pageable);

    /**
     * 특정 상태코드의 로그 검색
     */
    Page<ApiGatewayRequestLog> findByStatusCode(Integer statusCode, Pageable pageable);

    /**
     * 특정 ClientId 와 기간으로 로그 검색
     */
    @Query("{\"bool\": {\"must\": [{\"term\": {\"clientId\": \"?0\"}}, {\"range\": {\"timestamp\": {\"gte\": \"?1\", \"lte\": \"?2\"}}}]}}")
    Page<ApiGatewayRequestLog> findByClientIdAndTimestampBetween(
            String clientId, String startTime, String endTime, Pageable pageable);

    /**
     * 느린 응답 로그 검색 (3 초 이상)
     */
    @Query("{\"range\": {\"responseTimeMs\": {\"gte\": 3000}}}")
    Page<ApiGatewayRequestLog> findSlowRequests(Pageable pageable);

    /**
     * 특정 엔드포인트 로그 검색
     */
    Page<ApiGatewayRequestLog> findByEndpoint(String endpoint, Pageable pageable);

    /**
     * 보안 위험 로그 검색
     */
    Page<ApiGatewayRequestLog> findBySecurityRiskLevelIsNotNull(Pageable pageable);

    /**
     * 특정 공격 유형 로그 검색
     */
    Page<ApiGatewayRequestLog> findByAttackType(String attackType, Pageable pageable);

    /**
     * ClientIp 로 로그 검색
     */
    Page<ApiGatewayRequestLog> findByClientIp(String clientIp, Pageable pageable);

    /**
     * 특정 HTTP 메서드 로그 검색
     */
    Page<ApiGatewayRequestLog> findByMethod(String method, Pageable pageable);

    /**
     * 복합 검색: ClientId + StatusCode + 기간
     */
    @Query("{\"bool\": {\"must\": [" +
            "{\"term\": {\"clientId\": \"?0\"}}, " +
            "{\"term\": {\"statusCode\": ?1}}, " +
            "{\"range\": {\"timestamp\": {\"gte\": \"?2\", \"lte\": \"?3\"}}}" +
            "]}}")
    Page<ApiGatewayRequestLog> searchByClientIdAndStatusCodeAndTimestamp(
            String clientId, Integer statusCode, String startTime, String endTime, Pageable pageable);

    /**
     * URI 패턴 검색
     */
    @Query("{\"wildcard\": {\"uri.keyword\": \"*?0*\"}}")
    Page<ApiGatewayRequestLog> searchByUriPattern(String uriPattern, Pageable pageable);

    /**
     * 에러 메시지 검색 (Full-text)
     */
    @Query("{\"match\": {\"errorMessage\": \"?0\"}}")
    Page<ApiGatewayRequestLog> searchByErrorMessage(String errorMessage, Pageable pageable);

    /**
     * 특정 날짜의 로그 카운트
     */
    long countByDate(String date);

    /**
     * ClientId 별 로그 카운트
     */
    long countByClientId(String clientId);

    /**
     * 특정 상태코드 카운트
     */
    long countByStatusCode(Integer statusCode);
}
