package com.exec.services.apikey.service;

import com.exec.core.domain.ApiKey;
import com.exec.core.repository.ApiKeyRepository;
import com.exec.services.apikey.dto.SignatureTestRequest;
import com.exec.services.apikey.dto.SignatureTestResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exec.common.exception.BusinessException;
import com.exec.common.exception.ErrorCode;
import com.exec.common.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignatureTestService {

    private static final Map<String, String> ALGORITHM_MAP = Map.of(
            "HmacSHA256", "HmacSHA256",
            "HmacSHA384", "HmacSHA384",
            "HmacSHA512", "HmacSHA512",
            "HmacSHA1", "HmacSHA1",
            "HmacMD5", "HmacMD5"
    );

    // KST 타임존
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    // KST 기반 ISO 8601 형식: yyyy-MM-dd'T'HH:mm:ssXXX
    private static final DateTimeFormatter KST_ISO_8601_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
            .withZone(KST_ZONE);
    private final ApiKeyRepository apiKeyRepository;
    private final CryptoUtil cryptoUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 수동으로 입력한 Access Key 와 Secret Key 를 사용하여 Signature 생성
     */
    public SignatureTestResponse generateSignature(SignatureTestRequest request) {
        String accessKey = request.getAccessKey();
        String secretKey = request.getSecretKey();

        if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Access Key and Secret Key are required");
        }

        return createSignatureResponse(accessKey, secretKey, request);
    }

    /**
     * 저장된 API Key 를 사용하여 Signature 생성
     */
    public SignatureTestResponse generateSignatureWithStoredKey(String accessKey, SignatureTestRequest request) {
        ApiKey apiKey = apiKeyRepository.findByAccessKey(accessKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        return createSignatureResponse(accessKey, apiKey.getSecret(), request);
    }

    /**
     * Signature Response 생성 로직
     */
    private SignatureTestResponse createSignatureResponse(String accessKey, String secretKey,
                                                          SignatureTestRequest request) {
        try {
            // 타임스탬프 생성 (미입력 시 현재 KST 시간)
            String timestamp = request.getTimestamp();
            if (timestamp == null || timestamp.isEmpty()) {
                timestamp = KST_ISO_8601_FORMATTER.format(ZonedDateTime.now(KST_ZONE));
            }

            // IdempotencyKey 생성 (미입력 시 UUID 생성)
            String idempotencyKey = request.getIdempotencyKey();
            if (idempotencyKey == null || idempotencyKey.isEmpty()) {
                idempotencyKey = UUID.randomUUID().toString();
            }

            // Request Body 를 문자열로 변환
            String bodyString = convertRequestBodyToString(request.getRequestBody());
            if (bodyString == null) {
                bodyString = "";
            }

            // 알고리즘 결정 (기본값: HmacSHA256)
            String algorithm = request.getAlgorithm();
            if (algorithm == null || algorithm.isEmpty()) {
                algorithm = "HmacSHA256";
            }

            // 알고리즘 유효성 검증
            if (!ALGORITHM_MAP.containsKey(algorithm)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                        "Unsupported algorithm: " + algorithm + ". Supported: " + ALGORITHM_MAP.keySet());
            }

            // URI 와 Query String 처리
            String path = request.getUri();
            String queryString = request.getQueryString();

            // Query String 정규화 (1 회만 수행)
            String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

            // 전체 URI (디스플레이용)
            String fullUri = queryString != null && !queryString.isEmpty()
                    ? path + "?" + queryString
                    : path;

            // Body Hash 계산
            String bodyHash = cryptoUtil.calculateBodyHash(bodyString);

            // 이미 정규화된 canonicalQuery 를 사용하여 서명 생성 (정규화 중복 제거)
            String signature = cryptoUtil.generateSignature(
                    request.getMethod().toUpperCase(),
                    path,
                    canonicalQuery,
                    idempotencyKey,
                    bodyString,
                    timestamp,
                    secretKey,
                    true,  // useBodyHash = true
                    algorithm);

            // 서명에 사용된 메시지 재구성 (디버그 정보용 - canonicalQuery 와 일관성 유지)
            String message = request.getMethod().toUpperCase() + "\n" +
                    path + "\n" +
                    canonicalQuery + "\n" +
                    idempotencyKey + "\n" +
                    bodyHash + "\n" +
                    timestamp;

            // Authorization 헤더 값 생성 (동적 알고리즘 및 IdempotencyKey 포함)
            String authorizationHeader = String.format("algorithm=%s, access-key=%s, signed-date=%s, signature=%s, idempotency-key=%s",
                    algorithm, accessKey, timestamp, signature, idempotencyKey);

            // 전체 헤더 맵 생성
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", authorizationHeader);
            headers.put("Content-Type", request.getContentType());

            // Body Hash 헤더 추가 (디버깅 용도)
            headers.put("X-Body-Hash", bodyHash);

            // curl 예시 생성
            String curlExample = buildCurlExample(request, headers, fullUri);

            // 디버깅 정보 생성
            SignatureTestResponse.SignatureDebugInfo debugInfo = SignatureTestResponse.SignatureDebugInfo.builder()
                    .method(request.getMethod())
                    .uri(fullUri)
                    .contentType(request.getContentType())
                    .bodyHash(bodyHash) // Body Hash 포함
                    .canonicalRequest(message) // 서명에 사용된 메시지
                    .hmacAlgorithm(algorithm)
                    .build();

            return SignatureTestResponse.builder()
                    .signature(signature)
                    .authorizationHeader(authorizationHeader)
                    .headers(headers)
                    .stringToSign(message) // 서명에 사용된 메시지 (Body Hash 포함)
                    .timestamp(timestamp)
                    .accessKey(accessKey)
                    .curlExample(curlExample)
                    .debugInfo(debugInfo)
                    .build();

        } catch (Exception e) {
            log.error("Failed to generate signature", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate signature: " + e.getMessage());
        }
    }


    /**
     * Request Body 를 String 으로 변환
     */
    private String convertRequestBodyToString(Object requestBody) {
        if (requestBody == null) {
            return null;
        }

        if (requestBody instanceof String) {
            return (String) requestBody;
        }

        try {
            // JSON 객체인 경우 문자열로 직렬화
            return objectMapper.writeValueAsString(requestBody);
        } catch (Exception e) {
            log.warn("Failed to serialize request body to JSON", e);
            return requestBody.toString();
        }
    }

    /**
     * curl 명령어 예시 생성
     */
    private String buildCurlExample(SignatureTestRequest request, Map<String, String> headers, String fullUri) {
        StringBuilder curl = new StringBuilder("curl -X ");
        curl.append(request.getMethod());

        // 헤더 추가
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            curl.append(" \\\n  -H '").append(entry.getKey()).append(": ").append(entry.getValue()).append("'");
        }

        // Request Body 추가 (POST, PUT, PATCH 의 경우)
        String requestBodyString = convertRequestBodyToString(request.getRequestBody());
        if (requestBodyString != null && !requestBodyString.isEmpty()
                && !request.getMethod().equalsIgnoreCase("GET")
                && !request.getMethod().equalsIgnoreCase("DELETE")) {
            curl.append(" \\\n  -d '").append(requestBodyString).append("'");
        }

        // URL 추가 (query string 포함)
        curl.append(" \\\n  'http://localhost:18080").append(fullUri).append("'");

        return curl.toString();
    }
}