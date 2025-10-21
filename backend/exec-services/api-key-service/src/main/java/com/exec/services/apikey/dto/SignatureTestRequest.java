package com.exec.services.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureTestRequest {

    /**
     * API Access Key (선택적 - 직접 입력 시에만 사용)
     */
    private String accessKey;

    /**
     * API Secret Key (선택적 - 직접 입력 시에만 사용)
     */
    private String secretKey;

    /**
     * HTTP Method (GET, POST, PUT, DELETE 등)
     */
    @NotBlank(message = "HTTP Method is required")
    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE)$", message = "Invalid HTTP method")
    private String method;

    /**
     * 요청 URI (예: /api/v1/users)
     */
    @NotBlank(message = "URI is required")
    private String uri;

    /**
     * 쿼리 스트링 (선택적 - 예: status=active&page=1)
     */
    private String queryString;

    /**
     * 요청 Body (POST, PUT, PATCH 요청 시)
     * JSON 객체 또는 문자열 모두 허용
     */
    private Object requestBody;

    /**
     * 타임스탬프 (선택적 - 미입력 시 현재 시간 사용)
     * 형식: yyMMdd'T'HHmmss'Z' (예: 240101T123456Z)
     * PHP: date("ymd").'T'.date("His").'Z'
     */
    private String timestamp;

    /**
     * Idempotency Key (선택적)
     */
    private String idempotencyKey;

    /**
     * Content-Type (선택적 - 기본값: application/json)
     */
    @Builder.Default
    private String contentType = "application/json";

    /**
     * HMAC 알고리즘 (선택적 - 기본값: HmacSHA256)
     * 지원되는 값: HmacSHA256, HmacSHA384, HmacSHA512, HmacSHA1, HmacMD5
     */
    @Builder.Default
    private String algorithm = "HmacSHA256";
}