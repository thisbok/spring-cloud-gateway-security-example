package com.exec.api.gateway.filter;

import com.exec.api.gateway.config.RequestResponseLoggingConfig;
import com.exec.api.gateway.constants.ExchangeAttributes;
import com.exec.api.gateway.service.ApiCallLogService;
import com.exec.api.gateway.service.IdempotencyService;
import com.exec.api.gateway.util.IpAddressUtil;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 통합 로깅 필터 (Reactor Context 기반)
 * <p>
 * 기존 RequestResponseLoggingFilter + RequestIdResponseFilter 통합
 * <p>
 * 주요 기능:
 * - Request ID 생성 및 헤더 추가
 * - Request/Response Body 캡처
 * - API 호출 로깅 (MySQL, Elasticsearch, Kafka)
 * - Idempotency 완료 처리
 * <p>
 * Reactor Context 를 사용하여 필터 순서와 무관하게 동작합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntegratedLoggingFilter implements GlobalFilter, Ordered {

    // 민감정보 마스킹을 위한 정규식 패턴들
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|key|authorization)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
    );
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // Request-ID 헤더
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    private final ApiCallLogService apiCallLogService;
    private final IdempotencyService idempotencyService;
    private final RequestResponseLoggingConfig loggingConfig;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.info("IntegratedLoggingFilter executed for {} {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI());

        // 1. Request ID 생성/추출
        String requestId = generateOrExtractRequestId(exchange);
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);
        exchange.getAttributes().put(ExchangeAttributes.REQUEST_ID, requestId);

        // 2. 로깅 제외 경로 확인
        if (!loggingConfig.shouldLog(
                exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().value())) {
            log.info("Skipping logging for excluded path: {}", exchange.getRequest().getPath().value());
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();

        log.debug("[{}] Starting request logging for: {} {}",
                requestId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI()
        );

        // 3. Request Body 는 RequestBodyCacheFilter 에서 이미 캐싱됨
        // ExchangeAttributes.REQUEST_BODY 에서 가져다 사용
        exchange.getAttributes().put(ExchangeAttributes.START_TIME, startTime);

        // 4. Response Decorator 적용
        ServerHttpResponse decoratedResponse = createResponseDecorator(exchange, exchange.getResponse());
        ServerWebExchange decoratedExchange = exchange.mutate().response(decoratedResponse).build();

        // 5. 필터 체인 실행 및 완료 처리
        return chain.filter(decoratedExchange)
                .doFinally(signalType ->
                        handleCompletion(exchange, signalType)
                );
    }

    @Override
    public int getOrder() {
        // RequestBodyCacheFilter 다음에 실행
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * X-Request-ID 생성 또는 추출
     */
    private String generateOrExtractRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);

        if (requestId == null || requestId.trim().isEmpty()) {
            requestId = UUID.randomUUID().toString();
            log.debug("Generated new X-Request-ID: {}", requestId);
        } else {
            log.debug("Using existing X-Request-ID: {}", requestId);
        }

        return requestId;
    }

    /**
     * Response Body 캡처 Decorator 생성
     */
    private ServerHttpResponseDecorator createResponseDecorator(
            ServerWebExchange exchange,
            ServerHttpResponse originalResponse) {

        return new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return DataBufferUtils.join(Flux.from(body))
                        .switchIfEmpty(Mono.defer(() -> {
                            // 빈 응답 처리
                            exchange.getAttributes().put(ExchangeAttributes.RESPONSE_BODY, "");
                            return originalResponse.writeWith(Flux.empty()).then(Mono.empty());
                        }))
                        .flatMap(dataBuffer -> {
                            try {
                                // Response Body 추출
                                int bodySize = dataBuffer.readableByteCount();

                                if (bodySize > loggingConfig.getMaxBodySize()) {
                                    byte[] truncated = new byte[loggingConfig.getMaxBodySize()];
                                    dataBuffer.read(truncated);
                                    String truncatedBody = new String(truncated, StandardCharsets.UTF_8) + "... [TRUNCATED]";
                                    exchange.getAttributes().put(ExchangeAttributes.RESPONSE_BODY, truncatedBody);
                                } else {
                                    byte[] content = new byte[bodySize];
                                    dataBuffer.read(content);
                                    String responseBody = new String(content, StandardCharsets.UTF_8);
                                    exchange.getAttributes().put(ExchangeAttributes.RESPONSE_BODY, responseBody);
                                }

                                // DataBuffer 재사용
                                dataBuffer.readPosition(0);
                                return originalResponse.writeWith(Mono.just(dataBuffer));

                            } catch (Exception e) {
                                log.warn("Failed to capture response body", e);
                                exchange.getAttributes().put(ExchangeAttributes.RESPONSE_BODY, "[CAPTURE_FAILED]");
                                return originalResponse.writeWith(Mono.just(dataBuffer));
                            }
                        });
            }
        };
    }

    /**
     * 요청 완료 처리
     * - API 로깅
     * - Idempotency 완료 마킹
     */
    private void handleCompletion(
            ServerWebExchange exchange,
            SignalType signalType) {

        // API 로깅
        logApiCall(exchange, signalType);

        // Idempotency 완료 처리
        markIdempotencyCompleted(exchange);
    }

    /**
     * API 호출 로깅
     */
    private void logApiCall(
            ServerWebExchange exchange,
            SignalType signalType) {

        try {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            // Exchange Attributes 에서 데이터 추출
            String requestId = exchange.getAttribute(ExchangeAttributes.REQUEST_ID);
            // RequestBodyCacheFilter 가 저장한 body 사용
            String requestBody = exchange.getAttribute(ExchangeAttributes.REQUEST_BODY);
            String responseBody = exchange.getAttribute(ExchangeAttributes.RESPONSE_BODY);
            Long requestTime = exchange.getAttribute(ExchangeAttributes.START_TIME);
            String clientId = exchange.getAttribute(ExchangeAttributes.CLIENT_ID);
            Long apiKeyId = exchange.getAttribute(ExchangeAttributes.API_KEY_ID);
            String accessKey = exchange.getAttribute(ExchangeAttributes.ACCESS_KEY);
            String algorithm = exchange.getAttribute(ExchangeAttributes.ALGORITHM);
            String signature = exchange.getAttribute(ExchangeAttributes.SIGNATURE);
            String signedDate = exchange.getAttribute(ExchangeAttributes.SIGNED_DATE);
            String idempotencyKey = exchange.getAttribute(ExchangeAttributes.IDEMPOTENCY_KEY);

            long responseTime = requestTime != null
                    ? System.currentTimeMillis() - requestTime
                    : 0L;

            // 헤더 정보 수집
            Map<String, String> requestHeaders = collectHeaders(request.getHeaders());
            Map<String, String> responseHeaders = collectHeaders(response.getHeaders());

            // 민감정보 마스킹
            String maskedRequestBody = maskSensitiveData(requestBody);
            String maskedResponseBody = maskSensitiveData(responseBody);

            Integer statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : null;

            // 에러 메시지 추출
            String errorMessage = null;
            if (statusCode != null && statusCode >= 400 && responseBody != null) {
                try {
                    if (responseBody.contains("\"errorMessage\"")) {
                        int start = responseBody.indexOf("\"errorMessage\":\"") + 16;
                        int end = responseBody.indexOf("\"", start);
                        if (end > start) {
                            errorMessage = responseBody.substring(start, end);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract error message from response body", e);
                }
            }

            OffsetDateTime now = OffsetDateTime.now();

            ApiGatewayRequestLogDto logDto = ApiGatewayRequestLogDto.builder()
                    // 기본 식별 정보
                    .requestId(requestId)
                    .clientId(clientId)
                    .apiKeyId(apiKeyId)
                    .accessKey(accessKey)
                    .timestamp(now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .date(now.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))

                    // 요청 정보
                    .method(request.getMethod().name())
                    .uri(request.getURI().toString())
                    .queryString(request.getURI().getQuery())
                    .requestHeaders(requestHeaders)
                    .requestBody(maskedRequestBody)
                    .clientIp(IpAddressUtil.getClientIpAddressNormalized(request))

                    // 응답 정보
                    .statusCode(statusCode)
                    .responseHeaders(responseHeaders)
                    .responseBody(maskedResponseBody)
                    .responseTimeMs(responseTime)

                    // 메타데이터
                    .userAgent(request.getHeaders().getFirst("User-Agent"))
                    .contentType(request.getHeaders().getFirst("Content-Type"))

                    // 에러 정보
                    .errorMessage(errorMessage)
                    .signalType(signalType.name())  // Reactor Signal 타입 추가

                    // 인증 관련
                    .algorithm(algorithm)
                    .signature(signature)
                    .signedDate(signedDate)
                    .idempotencyKey(idempotencyKey)
                    .build();

            apiCallLogService.saveApiCallLogAsync(logDto);

        } catch (Exception e) {
            log.error("Failed to log API call", e);
        }
    }

    /**
     * Idempotency Key 완료 처리
     */
    private void markIdempotencyCompleted(ServerWebExchange exchange) {
        String accessKey = exchange.getAttribute(ExchangeAttributes.ACCESS_KEY);
        String idempotencyKey = exchange.getAttribute(ExchangeAttributes.IDEMPOTENCY_KEY);

        if (accessKey != null && idempotencyKey != null) {
            Integer statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : null;

            // Status Code 만 저장 (Idempotency 목적상 충분)
            String responseInfo = statusCode != null
                    ? String.valueOf(statusCode)
                    : "unknown";

            idempotencyService.markAsCompleted(accessKey, idempotencyKey, responseInfo)
                    .subscribe(
                            unused -> log.debug("Idempotency completed: accessKey={}, key={}, status={}",
                                    accessKey, idempotencyKey, responseInfo),
                            error -> log.error("Failed to mark idempotency as completed: accessKey={}, key={}",
                                    accessKey, idempotencyKey, error)
                    );
        }
    }

    /**
     * HTTP 헤더를 Map 으로 변환
     */
    private Map<String, String> collectHeaders(HttpHeaders headers) {
        Map<String, String> headerMap = new HashMap<>();

        headers.forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                String value = String.join(", ", values);

                if (value.length() > loggingConfig.getMaxHeaderValueLength()) {
                    value = value.substring(0, loggingConfig.getMaxHeaderValueLength()) + "... [TRUNCATED]";
                }

                headerMap.put(name, value);
            }
        });

        return headerMap;
    }

    /**
     * 민감정보 마스킹
     */
    private String maskSensitiveData(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        if (!loggingConfig.getSecurity().isMaskSensitiveData()) {
            return content;
        }

        String masked = content;

        // 패스워드/토큰 등 마스킹
        masked = PASSWORD_PATTERN.matcher(masked).replaceAll("$1\":\"***\"");

        // 신용카드 번호 마스킹
        if (loggingConfig.getSecurity().isMaskCreditCard()) {
            masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("****-****-****-****");
        }

        // 주민등록번호 마스킹
        masked = SSN_PATTERN.matcher(masked).replaceAll("***-**-****");

        return masked;
    }
}
