package com.exec.api.gateway.util;

import com.exec.api.gateway.dto.ApiCallLogData;
import lombok.extern.slf4j.Slf4j;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Map;

/**
 * Reactor Context 읽기/쓰기 편의성 제공 유틸리티
 * <p>
 * Reactor Context 는 불변 (Immutable) 이므로, 데이터를 저장하면
 * 새로운 Context 인스턴스가 반환됩니다.
 * <p>
 * 이 유틸리티는 Context 조작을 간편하게 하고,
 * API 로그 데이터 추출을 쉽게 해줍니다.
 */
@Slf4j
public class ReactiveContextHolder {

    // Context 키 상수
    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_BODY = "requestBody";
    public static final String RESPONSE_BODY = "responseBody";
    public static final String REQUEST_TIME = "requestTime";
    public static final String CLIENT_ID = "clientId";
    public static final String ACCESS_KEY = "accessKey";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
    public static final String API_KEY_ID = "apiKeyId";

    /**
     * Context 에 값 저장 (불변 Context 반환)
     *
     * @param ctx   기존 Context
     * @param key   저장할 키
     * @param value 저장할 값
     * @return 새로운 Context (불변)
     */
    public static Context put(Context ctx, String key, Object value) {
        return ctx.put(key, value);
    }

    /**
     * Context 에서 값 조회
     *
     * @param ctx Context
     * @param key 조회할 키
     * @param <T> 값의 타입
     * @return 조회된 값
     * @throws java.util.NoSuchElementException 키가 존재하지 않으면
     */
    public static <T> T get(Context ctx, String key) {
        return ctx.get(key);
    }

    /**
     * Context 에서 값 조회 (기본값 포함)
     *
     * @param ctx          Context
     * @param key          조회할 키
     * @param defaultValue 키가 없을 때 반환할 기본값
     * @param <T>          값의 타입
     * @return 조회된 값 또는 기본값
     */
    public static <T> T getOrDefault(Context ctx, String key, T defaultValue) {
        return ctx.getOrDefault(key, defaultValue);
    }

    /**
     * Context 에 여러 값 한 번에 저장
     *
     * @param ctx    기존 Context
     * @param values 저장할 키-값 맵
     * @return 새로운 Context (불변)
     */
    public static Context putAll(Context ctx, Map<String, Object> values) {
        Context newCtx = ctx;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            newCtx = newCtx.put(entry.getKey(), entry.getValue());
        }
        return newCtx;
    }

    /**
     * 현재 Context 에서 API 로그 데이터 추출
     * <p>
     * Context 에 저장된 요청 정보를 ApiCallLogData 로 변환합니다.
     * 키가 존재하지 않으면 기본값을 사용합니다.
     *
     * @param ctx Reactor Context
     * @return API 로그 데이터
     */
    public static ApiCallLogData extractLogData(Context ctx) {
        return ApiCallLogData.builder()
                .requestId(getOrDefault(ctx, REQUEST_ID, "unknown"))
                .requestBody(getOrDefault(ctx, REQUEST_BODY, ""))
                .responseBody(getOrDefault(ctx, RESPONSE_BODY, ""))
                .requestTime(getOrDefault(ctx, REQUEST_TIME, 0L))
                .clientId(getOrDefault(ctx, CLIENT_ID, null))
                .accessKey(getOrDefault(ctx, ACCESS_KEY, null))
                .idempotencyKey(getOrDefault(ctx, IDEMPOTENCY_KEY, null))
                .apiKeyId(getOrDefault(ctx, API_KEY_ID, null))
                .build();
    }

    /**
     * 현재 ContextView 에서 API 로그 데이터 추출
     * <p>
     * ContextView 에 저장된 요청 정보를 ApiCallLogData 로 변환합니다.
     * Mono.deferContextual() 에서 사용됩니다.
     *
     * @param ctx Reactor ContextView
     * @return API 로그 데이터
     */
    public static ApiCallLogData extractLogData(ContextView ctx) {
        return ApiCallLogData.builder()
                .requestId(ctx.getOrDefault(REQUEST_ID, "unknown"))
                .requestBody(ctx.getOrDefault(REQUEST_BODY, ""))
                .responseBody(ctx.getOrDefault(RESPONSE_BODY, ""))
                .requestTime(ctx.getOrDefault(REQUEST_TIME, 0L))
                .clientId(ctx.getOrDefault(CLIENT_ID, null))
                .accessKey(ctx.getOrDefault(ACCESS_KEY, null))
                .idempotencyKey(ctx.getOrDefault(IDEMPOTENCY_KEY, null))
                .apiKeyId(ctx.getOrDefault(API_KEY_ID, null))
                .build();
    }
}
