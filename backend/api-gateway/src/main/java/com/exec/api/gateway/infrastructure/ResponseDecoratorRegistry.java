package com.exec.api.gateway.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Response Decorator 중앙 관리 레지스트리
 * <p>
 * 여러 필터에서 Response Decorator 를 등록하고 조회할 수 있도록
 * 중앙에서 관리합니다. 필터 순서와 무관하게 동작하며,
 * Response Body 캡처를 안전하게 처리합니다.
 */
@Component
@Slf4j
public class ResponseDecoratorRegistry {

    private static final String REQUEST_ID_ATTRIBUTE = "request-id";

    private final Map<String, ServerHttpResponseDecorator> decorators = new ConcurrentHashMap<>();

    /**
     * Response Decorator 등록
     * <p>
     * 요청 ID 를 키로 사용하여 Decorator 를 등록합니다.
     * 여러 필터에서 호출해도 안전합니다 (ConcurrentHashMap 사용).
     *
     * @param exchange         ServerWebExchange
     * @param decoratorFactory Decorator 생성 함수
     */
    public void registerDecorator(
            ServerWebExchange exchange,
            Function<ServerHttpResponse, ServerHttpResponseDecorator> decoratorFactory) {

        String requestId = extractRequestId(exchange);
        if (requestId == null) {
            log.warn("Cannot register decorator: requestId is null");
            return;
        }

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decorator = decoratorFactory.apply(originalResponse);

        decorators.put(requestId, decorator);

        log.debug("Response decorator registered: requestId={}", requestId);
    }

    /**
     * Response Decorator 조회
     *
     * @param requestId 요청 ID
     * @return Decorator (존재하지 않으면 Optional.empty())
     */
    public Optional<ServerHttpResponseDecorator> getDecorator(String requestId) {
        return Optional.ofNullable(decorators.get(requestId));
    }

    /**
     * Response Decorator 정리
     * <p>
     * 메모리 누수 방지를 위해 요청 완료 시 호출되어야 합니다.
     *
     * @param requestId 요청 ID
     */
    public void clearDecorator(String requestId) {
        decorators.remove(requestId);
        log.debug("Response decorator cleared: requestId={}", requestId);
    }

    /**
     * Exchange Attributes 에서 Request ID 추출
     *
     * @param exchange ServerWebExchange
     * @return Request ID (없으면 null)
     */
    private String extractRequestId(ServerWebExchange exchange) {
        return exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
    }
}
