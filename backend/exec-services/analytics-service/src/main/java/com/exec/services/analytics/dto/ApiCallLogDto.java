package com.exec.services.analytics.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 📝 API 호출 로그 DTO (Analytics 서비스용)
 * <p>
 * Kafka 메시지로 수신되는 API 호출 로그 데이터
 */
@Getter
@Builder
@ToString
public class ApiCallLogDto {

    // 기본 식별 정보
    private String requestId;
    private String clientId;
    private Long apiKeyId;
    private LocalDateTime timestamp;

    // 요청 정보
    private String method;
    private String uri;
    private String queryString;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private String clientIp;

    // 응답 정보
    private Integer statusCode;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private Long responseTimeMs;

    // 메타데이터
    private Long requestSize;
    private Long responseSize;
    private String userAgent;
    private String contentType;

    // 성능 메트릭
    private Long startTimeMs;
    private Long endTimeMs;

    // 에러 정보
    private String errorMessage;

    // 보안 관련
    private String securityRiskLevel;
    private String attackType;

    /**
     * 성공 여부 판단
     */
    public Boolean getIsSuccess() {
        return statusCode != null && statusCode < 400;
    }

    /**
     * 에러 여부 판단
     */
    public Boolean getHasError() {
        return !getIsSuccess();
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
}