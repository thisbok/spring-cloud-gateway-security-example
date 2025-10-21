package com.exec.api.gateway.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * API 호출 로그 데이터 전송 객체
 * <p>
 * 모든 API 요청/응답 정보를 포함하는 완전한 로그 구조
 * MySQL, Elasticsearch, Kafka 등 다중 채널 저장을 위한 통합 모델
 */
@Getter
@Builder
@ToString
public class ApiCallLogDto {

    // ==================== 기본 식별 정보 ====================

    /**
     * 클라이언트 ID
     */
    private String clientId;

    /**
     * API Key ID
     */
    private Long apiKeyId;

    /**
     * 요청 시작 시간
     */
    private LocalDateTime timestamp;

    // ==================== 요청 정보 ====================

    /**
     * HTTP 메서드 (GET, POST, PUT, DELETE 등)
     */
    private String method;

    /**
     * 요청 URI (경로 및 엔드포인트)
     */
    private String uri;

    /**
     * 쿼리 스트링 (URL 파라미터)
     */
    private String queryString;

    /**
     * 요청 헤더 전체 맵
     */
    private Map<String, String> requestHeaders;

    /**
     * 요청 바디 (JSON, XML, Form 데이터 등)
     */
    private String requestBody;

    /**
     * 클라이언트 IP 주소
     */
    private String clientIp;

    // ==================== 응답 정보 ====================

    /**
     * HTTP 응답 상태 코드
     */
    private Integer statusCode;

    /**
     * 응답 헤더 전체 맵
     */
    private Map<String, String> responseHeaders;

    /**
     * 응답 바디 (JSON, XML, HTML 등)
     */
    private String responseBody;

    /**
     * 응답 시간 (밀리초)
     */
    private Long responseTimeMs;

    // ==================== 메타데이터 ====================

    /**
     * 요청 크기 (바이트)
     */
    private Long requestSize;

    /**
     * 응답 크기 (바이트)
     */
    private Long responseSize;

    /**
     * User-Agent 정보
     */
    private String userAgent;

    /**
     * Content-Type 정보
     */
    private String contentType;

    /**
     * 요청이 성공했는지 여부 (2xx 상태코드)
     */
    private Boolean isSuccess;

    /**
     * 오류 발생 여부
     */
    private Boolean hasError;

    /**
     * 오류 메시지 (있는 경우)
     */
    private String errorMessage;

    // ==================== 비즈니스 메타데이터 ====================

    /**
     * API 카테고리 (결제, 인증, 조회 등)
     */
    private String apiCategory;

    /**
     * API 버전 정보
     */
    private String apiVersion;

    /**
     * 세션 ID (있는 경우)
     */
    private String sessionId;

    /**
     * 사용자 ID (있는 경우)
     */
    private String userId;

    // ==================== 성능 메트릭 ====================

    /**
     * 요청 처리 시작 시간 (밀리초 타임스탬프)
     */
    private Long startTimeMs;

    /**
     * 요청 처리 종료 시간 (밀리초 타임스탬프)
     */
    private Long endTimeMs;

    /**
     * 업스트림 서비스 응답 시간 (밀리초)
     */
    private Long upstreamResponseTimeMs;

    // ==================== 보안 관련 ====================

    /**
     * 보안 위험도 레벨 (LOW, MEDIUM, HIGH)
     */
    private String securityRiskLevel;

    /**
     * 탐지된 공격 유형 (있는 경우)
     */
    private String attackType;

    /**
     * 지리적 위치 정보 (국가, 지역)
     */
    private String geoLocation;

    /**
     * 성공 여부를 상태 코드 기반으로 판단
     */
    public Boolean getIsSuccess() {
        if (statusCode == null) return false;
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 오류 여부를 상태 코드 기반으로 판단
     */
    public Boolean getHasError() {
        if (statusCode == null) return true;
        return statusCode >= 400;
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
     * API 엔드포인트 추출 (URI 에서 경로만)
     */
    public String getEndpoint() {
        if (uri == null) return null;

        // 쿼리 파라미터 제거
        int queryIndex = uri.indexOf('?');
        if (queryIndex > 0) {
            return uri.substring(0, queryIndex);
        }
        return uri;
    }
}