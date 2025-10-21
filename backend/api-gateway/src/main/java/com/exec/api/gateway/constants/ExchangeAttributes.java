package com.exec.api.gateway.constants;

/**
 * ServerWebExchange Attribute 키 상수 정의
 * <p>
 * Gateway Filter 간 데이터 전달을 위한 표준 Attribute 키 모음
 *
 * @since 1.0.0
 */
public final class ExchangeAttributes {

    // 인증 관련
    public static final String CLIENT_ID = "CLIENT_ID";
    public static final String API_KEY_ID = "API_KEY_ID";
    public static final String ACCESS_KEY = "ACCESS_KEY";
    public static final String IDEMPOTENCY_KEY = "IDEMPOTENCY_KEY";
    public static final String AUTH_CREDENTIALS = "AUTH_CREDENTIALS";
    public static final String ALGORITHM = "ALGORITHM";
    public static final String SIGNATURE = "SIGNATURE";
    public static final String SIGNED_DATE = "SIGNED_DATE";

    // 요청/응답 추적
    public static final String REQUEST_ID = "request-id";
    public static final String START_TIME = "request-start-time";

    // 요청/응답 바디 캐싱
    public static final String REQUEST_BODY = "CACHED_REQUEST_BODY";
    public static final String RESPONSE_BODY = "CACHED_RESPONSE_BODY";

    // 요청/응답 크기
    public static final String REQUEST_SIZE = "request-size";
    public static final String RESPONSE_SIZE = "response-size";

    private ExchangeAttributes() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
