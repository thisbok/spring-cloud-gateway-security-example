package com.exec.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CryptoUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 개선된 서명 데이터를 구성합니다. (Path, Query 분리, Body Hash, Timestamp)
     * Format: METHOD\nPATH\nCANONICAL_QUERY\nIDEMPOTENCY_KEY\nBODY_CONTENT\nTIMESTAMP
     *
     * @param method         HTTP 메서드
     * @param path           URI 경로
     * @param canonicalQuery 정규화된 쿼리 문자열
     * @param idempotencyKey 멱등성 키
     * @param bodyContent    Body 내용 (hash 또는 raw)
     * @param timestamp      타임스탬프
     * @return 서명 데이터
     */
    private String buildSignatureData(String method, String path, String canonicalQuery,
                                      String idempotencyKey, String bodyContent, String timestamp) {
        return method + "\n" +
                path + "\n" +
                (canonicalQuery == null ? "" : canonicalQuery) + "\n" +
                idempotencyKey + "\n" +
                (bodyContent == null ? "" : bodyContent) + "\n" +
                (timestamp == null ? "" : timestamp);
    }

    /**
     * 바이트 배열을 HEX 문자열로 변환합니다.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 서명을 위한 Body 준비 - 단순하고 명확한 처리
     *
     * @param body        원본 body
     * @param useBodyHash true 면 SHA256 해시, false 면 정규화된 body 반환
     * @return 처리된 body 문자열
     */
    public String prepareBodyForSignature(String body, boolean useBodyHash) {
        // null/empty 체크를 먼저 수행
        if (body == null || body.isEmpty()) {
            return "";
        }

        if (useBodyHash) {
            // Hash 를 사용하는 경우 calculateBodyHash 에서 정규화 수행
            return calculateBodyHash(body);
        } else {
            // Hash 를 사용하지 않는 경우 정규화만 수행
            return normalizeJson(body);
        }
    }

    /**
     * JSON 문자열을 정규화합니다 (공백 제거, 일관된 포맷)
     *
     * @param jsonString 원본 JSON 문자열
     * @return 정규화된 JSON 문자열
     */
    private String normalizeJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return jsonString;
        }

        // JSON 인지 간단히 확인 (시작이 { 또는 [인 경우)
        String trimmed = jsonString.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return jsonString; // JSON 이 아니면 원본 반환
        }

        try {
            // JSON 파싱 후 재직렬화하여 정규화
            Object json = objectMapper.readValue(jsonString, Object.class);
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            // JSON 파싱 실패 시 원본 반환
            log.debug("Failed to normalize JSON, using original: {}", e.getMessage());
            return jsonString;
        }
    }

    /**
     * Request Body 의 SHA256 해시를 생성합니다.
     * JSON 인 경우 정규화 후 해시를 생성합니다.
     *
     * @param body 요청 본문
     * @return HEX 인코딩된 SHA256 해시
     */
    public String calculateBodyHash(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }

        try {
            // JSON 정규화 시도
            String normalizedBody = normalizeJson(body);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedBody.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to calculate SHA-256 hash", e);
            throw new RuntimeException("Failed to calculate body hash", e);
        }
    }

    /**
     * Query Parameters 를 정규화합니다.
     * - 파라미터를 알파벳 순으로 정렬
     * - key=value 형식으로 연결
     * - & 로 조인
     *
     * @param queryString 원본 query string (예: "b=2&a=1&c=3")
     * @return 정규화된 query string (예: "a=1&b=2&c=3")
     */
    public String canonicalizeQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }

        // TreeMap 을 사용하여 자동 정렬
        Map<String, String> params = new TreeMap<>();

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                params.put(keyValue[0], keyValue[1]);
            } else if (keyValue.length == 1) {
                params.put(keyValue[0], "");
            }
        }

        // 정렬된 순서로 재구성
        return params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * HMAC 서명을 검증합니다. (동적 알고리즘 지원)
     *
     * @param data              서명할 데이터
     * @param secretKey         서명에 사용할 비밀 키
     * @param providedSignature 클라이언트가 제공한 서명 (Hex 형식)
     * @param algorithm         HMAC 알고리즘 (예: HmacSHA256, HmacSHA512)
     * @return 서명이 유효하면 true
     */
    public boolean verifyHmacSignature(String data, String secretKey, String providedSignature, String algorithm) {
        try {
            if (data == null || secretKey == null || providedSignature == null) {
                log.warn("Null parameters provided for HMAC verification");
                return false;
            }

            // HMAC 서명 생성 (동적 알고리즘)
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    algorithm
            );
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String computedSignature = bytesToHex(hmacBytes);

            // 타이밍 공격 방지를 위한 상수 시간 비교
            boolean isValid = constantTimeEquals(computedSignature.toLowerCase(), providedSignature.toLowerCase());

            if (!isValid) {
                log.warn("HMAC signature verification failed for algorithm: {}", algorithm);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Failed to verify HMAC signature with algorithm: {}", algorithm, e);
            return false;
        }
    }

    /**
     * API 요청 서명 검증 (이미 정규화된 canonicalQuery 사용)
     * <p>
     * 외부에서 이미 정규화를 수행한 경우 중복 정규화를 피하기 위한 메서드
     *
     * @param method            HTTP 메서드
     * @param path              URI 경로 (쿼리 제외)
     * @param canonicalQuery    이미 정규화된 쿼리 문자열
     * @param idempotencyKey    멱등성 키
     * @param body              요청 본문
     * @param timestamp         타임스탬프
     * @param secretKey         비밀 키
     * @param providedSignature 제공된 서명
     * @param useBodyHash       Body Hash 사용 여부
     * @param algorithm         HMAC 알고리즘
     * @return 서명이 유효하면 true
     */
    public boolean verifyApiRequestSignature(
            String method, String path, String canonicalQuery, String idempotencyKey,
            String body, String timestamp, String secretKey,
            String providedSignature, boolean useBodyHash, String algorithm) {
        try {
            // Body 처리
            String bodyContent = prepareBodyForSignature(body, useBodyHash);

            // 서명 데이터 구성 (canonicalQuery 는 이미 정규화됨)
            String signatureData = buildSignatureData(method, path, canonicalQuery,
                    idempotencyKey, bodyContent, timestamp);

            // HMAC 서명 검증
            return verifyHmacSignature(signatureData, secretKey, providedSignature, algorithm);

        } catch (Exception e) {
            log.error("Failed to verify API signature with canonical query, algorithm: {}", algorithm, e);
            return false;
        }
    }

    /**
     * API 요청 서명 생성 (이미 정규화된 canonicalQuery 사용)
     * <p>
     * 외부에서 이미 정규화를 수행한 경우 중복 정규화를 피하기 위한 메서드
     *
     * @param method         HTTP 메서드
     * @param path           URI 경로 (쿼리 제외)
     * @param canonicalQuery 이미 정규화된 쿼리 문자열
     * @param idempotencyKey 멱등성 키
     * @param body           요청 본문
     * @param timestamp      타임스탬프
     * @param secretKey      비밀 키
     * @param useBodyHash    Body Hash 사용 여부
     * @param algorithm      HMAC 알고리즘
     * @return HEX 인코딩된 서명
     */
    public String generateSignature(
            String method, String path, String canonicalQuery, String idempotencyKey,
            String body, String timestamp, String secretKey,
            boolean useBodyHash, String algorithm) {
        try {
            // Body 처리
            String bodyContent = prepareBodyForSignature(body, useBodyHash);

            // 서명 데이터 구성 (canonicalQuery 는 이미 정규화됨)
            String signatureData = buildSignatureData(method, path, canonicalQuery,
                    idempotencyKey, bodyContent, timestamp);

            // HMAC 서명 생성
            Mac mac = Mac.getInstance(algorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    algorithm
            );
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(signatureData.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);

        } catch (Exception e) {
            log.error("Failed to generate API signature with canonical query", e);
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * 타이밍 공격을 방지하는 상수 시간 문자열 비교
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }
}