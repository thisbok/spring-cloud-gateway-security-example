package com.exec.api.gateway.util;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorization 헤더 파싱 유틸리티
 * <p>
 * 지원 형식:
 * "algorithm={algo}, access-key={key}, signed-date={timestamp}, signature={sig}, idempotency-key={id}"
 * <p>
 * 서명 생성 방식:
 * - 메시지: Method + URI + IdempotencyKey + Body + Timestamp
 * - 서명: HMAC(message, secretKey) with specified algorithm
 * <p>
 * 지원 알고리즘:
 * - HmacSHA256 (기본값)
 * - HmacSHA384
 * - HmacSHA512
 * - HmacSHA1
 * - HmacMD5
 * <p>
 * IdempotencyKey:
 * - 멱등성 보장 및 Replay Attack 방지를 동시에 제공
 * - 상태 변경 요청 (POST/PUT/DELETE) 에 권장
 * - 재시도 시 동일 응답 반환
 */
@Slf4j
@Component
public class AuthorizationHeaderParser {
    private static final Pattern AUTH_PATTERN = Pattern.compile(
            "([\\w-]+)=([^,\\s]+)(?:,\\s*|$)"
    );

    /**
     * Authorization 헤더 파싱
     *
     * @param authHeader Authorization 헤더 값
     * @return 파싱된 인증 정보
     */
    public AuthCredentials parseAuthorizationHeader(String authHeader) {
        if (authHeader == null || authHeader.isEmpty()) {
            log.debug("Authorization header is empty");
            return null;
        }

        // 파라미터 파싱
        Map<String, String> paramMap = new HashMap<>();
        Matcher matcher = AUTH_PATTERN.matcher(authHeader);

        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            paramMap.put(key, value);
            log.debug("Parsed auth parameter: {}={}", key, value);
        }

        // 파라미터 추출 (algorithm 추가)
        String algorithm = paramMap.get("algorithm");
        String accessKey = paramMap.get("access-key");
        String signature = paramMap.get("signature");
        String signedDate = paramMap.get("signed-date");
        String idempotencyKey = paramMap.get("idempotency-key");

        // 필수 파라미터 검증
        if (accessKey == null || signature == null || signedDate == null || idempotencyKey == null) {
            log.warn("Missing required parameters in Authorization header. access-key: {}, signature: {}, signed-date: {}, idempotency-key: {}",
                    accessKey != null, signature != null, signedDate != null, idempotencyKey != null);
            return null;
        }

        // algorithm 이 없으면 기본값 HmacSHA256 사용
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = "HmacSHA256";
            log.debug("Algorithm not specified in Authorization header, using default: {}", algorithm);
        }

        return AuthCredentials.builder()
                .algorithm(algorithm)
                .accessKey(accessKey)
                .signature(signature)
                .signedDate(signedDate)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    /**
     * 인증 정보 DTO
     */
    @Getter
    @Builder
    public static class AuthCredentials {
        private final String algorithm;        // HMAC 알고리즘 (HmacSHA256, HmacSHA512 등)
        private final String accessKey;
        private final String signature;
        private final String signedDate;
        private final String idempotencyKey;  // 멱등성 보장 + Replay Attack 방지
    }
}