package com.exec.api.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 호출 로그 데이터 DTO
 * <p>
 * Reactor Context 에서 추출한 API 호출 관련 데이터를 담는 DTO 입니다.
 * IntegratedLoggingFilter 에서 로깅 및 Kafka 전송에 사용됩니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiCallLogData {

    /**
     * 요청 ID (추적용)
     */
    private String requestId;

    /**
     * 요청 Body
     */
    private String requestBody;

    /**
     * 응답 Body
     */
    private String responseBody;

    /**
     * 요청 시작 시각 (밀리초)
     */
    private Long requestTime;

    /**
     * 클라이언트 ID
     */
    private String clientId;

    /**
     * Access Key
     */
    private String accessKey;

    /**
     * Idempotency Key
     */
    private String idempotencyKey;

    /**
     * API Key ID
     */
    private Long apiKeyId;
}
