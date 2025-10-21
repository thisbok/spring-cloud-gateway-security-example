package com.exec.services.analytics.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.Map;

/**
 * Elasticsearch Document: API Gateway 요청 로그
 * <p>
 * ApiGatewayRequestLogDto 를 Elasticsearch 에 저장하기 위한 Document 엔티티
 * <p>
 * 인덱스 네이밍: api-gateway-logs-{yyyy.MM.dd}
 */
@Slf4j
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(
        indexName = "#{@elasticsearchIndexConfig.getTodayIndexName()}",
        createIndex = false,
        writeTypeHint = WriteTypeHint.FALSE
)
@Setting(shards = 5, replicas = 1, refreshInterval = "5s")
public class ApiGatewayRequestLog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 기본 식별 정보 ====================

    /**
     * requestId 를 Elasticsearch _id 로 사용하여 다음을 보장:
     * 1. 중복 방지: 같은 requestId 는 덮어쓰기됨 (Upsert)
     * 2. Kafka 재처리 멱등성: 재시도 시 중복 저장 방지
     */
    @Id
    @Field(type = FieldType.Keyword)
    private String requestId;

    @Field(type = FieldType.Keyword)
    private String clientId;

    @Field(type = FieldType.Long)
    private Long apiKeyId;

    @Field(type = FieldType.Keyword)
    private String accessKey;

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private String timestamp;

    @Field(type = FieldType.Keyword)
    private String date;

    // ==================== 요청 정보 ====================

    @Field(type = FieldType.Keyword)
    private String method;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String uri;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String queryString;

    @Field(type = FieldType.Object, enabled = true)
    private Map<String, String> requestHeaders;

    /**
     * 요청 본문 (JSON Object)
     * <p>
     * Kibana 에서 가독성 향상을 위해 JSON 파싱 후 객체로 저장
     */
    @Field(type = FieldType.Object, enabled = false)
    private Object requestBody;

    @Field(type = FieldType.Ip)
    private String clientIp;

    // ==================== 응답 정보 ====================

    @Field(type = FieldType.Integer)
    private Integer statusCode;

    @Field(type = FieldType.Object, enabled = true)
    private Map<String, String> responseHeaders;

    /**
     * 응답 본문 (JSON Object)
     * <p>
     * Kibana 에서 가독성 향상을 위해 JSON 파싱 후 객체로 저장
     */
    @Field(type = FieldType.Object, enabled = false)
    private Object responseBody;

    @Field(type = FieldType.Long)
    private Long responseTimeMs;

    // ==================== 메타데이터 ====================

    @Field(type = FieldType.Long)
    private Long requestSize;

    @Field(type = FieldType.Long)
    private Long responseSize;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String userAgent;

    @Field(type = FieldType.Keyword)
    private String contentType;

    // ==================== 성능 메트릭 ====================

    @Field(type = FieldType.Long)
    private Long startTimeMs;

    @Field(type = FieldType.Long)
    private Long endTimeMs;

    @Field(type = FieldType.Long)
    private Long upstreamResponseTimeMs;

    // ==================== 에러 정보 ====================

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword)
    )
    private String errorMessage;

    @Field(type = FieldType.Keyword)
    private String signalType;

    // ==================== 보안 관련 ====================

    @Field(type = FieldType.Keyword)
    private String securityRiskLevel;

    @Field(type = FieldType.Keyword)
    private String attackType;

    @Field(type = FieldType.Keyword)
    private String geoLocation;

    // ==================== 비즈니스 메타데이터 ====================

    @Field(type = FieldType.Keyword)
    private String apiCategory;

    @Field(type = FieldType.Keyword)
    private String apiVersion;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String userId;

    // ==================== 인증 관련 ====================

    @Field(type = FieldType.Keyword)
    private String algorithm;

    @Field(type = FieldType.Keyword, index = false)
    private String signature;

    @Field(type = FieldType.Keyword)
    private String signedDate;

    @Field(type = FieldType.Keyword)
    private String idempotencyKey;

    // ==================== 계산된 필드 ====================

    @Field(type = FieldType.Boolean)
    private Boolean isSuccess;

    @Field(type = FieldType.Boolean)
    private Boolean hasError;

    @Field(type = FieldType.Keyword)
    private String endpoint;

    @Field(type = FieldType.Long)
    private Long totalDataSize;

    // ==================== 변환 메서드 ====================

    /**
     * DTO → Document 변환
     */
    public static ApiGatewayRequestLog from(ApiGatewayRequestLogDto dto) {
        if (dto == null) {
            return null;
        }

        // 디버그: 원본 JSON 문자열 확인
        if (log.isDebugEnabled()) {
            log.debug("Converting DTO to Document - requestId: {}", dto.getRequestId());
            log.debug("Original requestBody: {}", dto.getRequestBody());
            log.debug("Original responseBody: {}", dto.getResponseBody());
        }

        return ApiGatewayRequestLog.builder()
                .requestId(dto.getRequestId())
                .clientId(dto.getClientId())
                .apiKeyId(dto.getApiKeyId())
                .accessKey(dto.getAccessKey())
                .timestamp(dto.getTimestamp())
                .date(dto.getDate())
                .method(dto.getMethod())
                .uri(dto.getUri())
                .queryString(dto.getQueryString())
                .requestHeaders(dto.getRequestHeaders())
                .requestBody(parseJson(dto.getRequestBody()))
                .clientIp(dto.getClientIp())
                .statusCode(dto.getStatusCode())
                .responseHeaders(dto.getResponseHeaders())
                .responseBody(parseJson(dto.getResponseBody()))
                .responseTimeMs(dto.getResponseTimeMs())
                .requestSize(dto.getRequestSize())
                .responseSize(dto.getResponseSize())
                .userAgent(dto.getUserAgent())
                .contentType(dto.getContentType())
                .startTimeMs(dto.getStartTimeMs())
                .endTimeMs(dto.getEndTimeMs())
                .upstreamResponseTimeMs(dto.getUpstreamResponseTimeMs())
                .errorMessage(dto.getErrorMessage())
                .signalType(dto.getSignalType())
                .securityRiskLevel(dto.getSecurityRiskLevel())
                .attackType(dto.getAttackType())
                .geoLocation(dto.getGeoLocation())
                .apiCategory(dto.getApiCategory())
                .apiVersion(dto.getApiVersion())
                .sessionId(dto.getSessionId())
                .userId(dto.getUserId())
                .algorithm(dto.getAlgorithm())
                .signature(dto.getSignature())
                .signedDate(dto.getSignedDate())
                .idempotencyKey(dto.getIdempotencyKey())
                .isSuccess(dto.getIsSuccess())
                .hasError(dto.getHasError())
                .endpoint(dto.getEndpoint())
                .totalDataSize(dto.getTotalDataSize())
                .build();
    }

    /**
     * JSON 문자열을 Object 로 파싱
     * <p>
     * Kibana 에서 가독성 향상을 위해 JSON 을 파싱합니다.
     * 파싱 실패 시 null 반환하여 원본 문자열 필드만 저장됩니다.
     */
    private static Object parseJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }

        // JSON 이 아닌 경우 (일반 텍스트) null 반환
        if (!jsonString.trim().startsWith("{") && !jsonString.trim().startsWith("[")) {
            return null;
        }

        try {
            // JSON Object 또는 Array 로 파싱
            return OBJECT_MAPPER.readValue(jsonString, Object.class);
        } catch (Exception e) {
            log.debug("Failed to parse JSON, will store as string only: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * Object 를 JSON 문자열로 변환
     * <p>
     * ES 에서 조회 시 Object 를 다시 JSON 문자열로 변환하여 DTO 에 담습니다.
     */
    private static String objectToJson(Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to convert object to JSON string: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Document → DTO 변환
     */
    public ApiGatewayRequestLogDto toDto() {
        return ApiGatewayRequestLogDto.builder()
                .requestId(this.requestId)
                .clientId(this.clientId)
                .apiKeyId(this.apiKeyId)
                .accessKey(this.accessKey)
                .timestamp(this.timestamp)
                .date(this.date)
                .method(this.method)
                .uri(this.uri)
                .queryString(this.queryString)
                .requestHeaders(this.requestHeaders)
                .requestBody(objectToJson(this.requestBody))
                .clientIp(this.clientIp)
                .statusCode(this.statusCode)
                .responseHeaders(this.responseHeaders)
                .responseBody(objectToJson(this.responseBody))
                .responseTimeMs(this.responseTimeMs)
                .requestSize(this.requestSize)
                .responseSize(this.responseSize)
                .userAgent(this.userAgent)
                .contentType(this.contentType)
                .startTimeMs(this.startTimeMs)
                .endTimeMs(this.endTimeMs)
                .upstreamResponseTimeMs(this.upstreamResponseTimeMs)
                .errorMessage(this.errorMessage)
                .signalType(this.signalType)
                .securityRiskLevel(this.securityRiskLevel)
                .attackType(this.attackType)
                .geoLocation(this.geoLocation)
                .apiCategory(this.apiCategory)
                .apiVersion(this.apiVersion)
                .sessionId(this.sessionId)
                .userId(this.userId)
                .algorithm(this.algorithm)
                .signature(this.signature)
                .signedDate(this.signedDate)
                .idempotencyKey(this.idempotencyKey)
                .build();
    }
}
