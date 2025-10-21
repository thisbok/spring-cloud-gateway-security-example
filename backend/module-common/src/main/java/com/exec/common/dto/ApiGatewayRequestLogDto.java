package com.exec.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

/**
 * 📊 API Gateway 요청 로그 DTO
 * <p>
 * Gateway 와 Analytics Service 간 Kafka 메시지 전송용 공통 DTO
 * <p>
 * 특징:
 * - Serializable: Kafka 직렬화 지원
 * - JsonInclude.NON_NULL: null 필드 제외하여 메시지 크기 최적화
 * - 불변 객체: Builder 패턴으로 생성 후 변경 불가
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiGatewayRequestLogDto implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 기본 식별 정보 ====================

    private String requestId;

    private String clientId;

    private Long apiKeyId;

    private String accessKey;

    /**
     * ISO 8601 포맷 타임스탬프 (예: "2025-10-14T15:30:45.123+09:00")
     */
    private String timestamp;

    /**
     * 날짜 (yyyy.MM.dd 형식, Elasticsearch 인덱스명용)
     */
    private String date;

    // ==================== 요청 정보 ====================

    private String method;

    private String uri;

    private String queryString;

    private Map<String, String> requestHeaders;

    private String requestBody;

    private String clientIp;

    // ==================== 응답 정보 ====================

    private Integer statusCode;

    private Map<String, String> responseHeaders;

    private String responseBody;

    private Long responseTimeMs;

    // ==================== 메타데이터 ====================

    private Long requestSize;

    private Long responseSize;

    private String userAgent;

    private String contentType;

    // ==================== 성능 메트릭 ====================

    private Long startTimeMs;

    private Long endTimeMs;

    private Long upstreamResponseTimeMs;

    // ==================== 에러 정보 ====================

    private String errorMessage;

    /**
     * Reactor Signal 타입 (ON_COMPLETE, ON_ERROR, CANCEL 등)
     */
    private String signalType;

    // ==================== 보안 관련 ====================

    private String securityRiskLevel;

    private String attackType;

    private String geoLocation;

    // ==================== 비즈니스 메타데이터 ====================

    private String apiCategory;

    private String apiVersion;

    private String sessionId;

    private String userId;

    // ==================== 인증 관련 ====================

    private String algorithm;

    private String signature;

    private String signedDate;

    private String idempotencyKey;

    // ==================== 계산 메서드 ====================

    /**
     * 성공 여부 판단
     */
    public Boolean getIsSuccess() {
        if (statusCode == null) return false;
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 에러 여부 판단
     */
    public Boolean getHasError() {
        if (statusCode == null) return true;
        return statusCode >= 400;
    }

    /**
     * 엔드포인트 추출 (쿼리 파라미터 제외)
     */
    public String getEndpoint() {
        if (uri == null) return "unknown";

        int queryIndex = uri.indexOf('?');
        return queryIndex > 0 ? uri.substring(0, queryIndex) : uri;
    }

    /**
     * 전체 데이터 크기 계산
     */
    public Long getTotalDataSize() {
        long total = 0;
        if (requestSize != null) total += requestSize;
        if (responseSize != null) total += responseSize;
        return total;
    }

    /**
     * 고속 응답 여부 (1 초 이내)
     */
    public boolean isFastResponse() {
        return responseTimeMs != null && responseTimeMs < 1000;
    }

    /**
     * 느린 응답 여부 (3 초 이상)
     */
    public boolean isSlowResponse() {
        return responseTimeMs != null && responseTimeMs >= 3000;
    }

    /**
     * 서버 에러 여부 (5xx)
     */
    public boolean isServerError() {
        return statusCode != null && statusCode >= 500;
    }

    /**
     * 클라이언트 에러 여부 (4xx)
     */
    public boolean isClientError() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }
}
