package com.exec.api.gateway.filter;

import com.exec.api.gateway.constants.ExchangeAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Request Body 캐싱 전용 필터
 * <p>
 * 모든 필터 중 가장 먼저 실행되어 Request Body 를 캐싱하고,
 * 다른 필터들이 ExchangeAttributes.REQUEST_BODY 를 통해 사용할 수 있도록 함
 * <p>
 * 주요 기능:
 * - Request Body 를 한 번만 읽어서 메모리에 캐싱
 * - ExchangeAttributes.REQUEST_BODY 에 저장
 * - Request 를 재구성하여 다음 필터들이 body 를 읽을 수 있도록 함
 * <p>
 * 장점:
 * - 단일 책임: Body 캐싱은 이 필터만 담당
 * - 성능: Body 를 한 번만 읽음
 * - 단순성: 다른 필터들은 attribute 에서 읽기만 하면 됨
 */
@Component
@Slf4j
public class RequestBodyCacheFilter implements GlobalFilter, Ordered {

    public RequestBodyCacheFilter() {
        log.info("=== RequestBodyCacheFilter Bean created and registered ===");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        log.info("RequestBodyCacheFilter executed for {} {}", request.getMethod(), request.getURI());

        // GET 요청이나 바디가 없는 경우 캐싱 스킵
        if (request.getHeaders().getContentLength() <= 0) {
            log.info("Skipping body cache - no content (Content-Length: {})", request.getHeaders().getContentLength());
            exchange.getAttributes().put(ExchangeAttributes.REQUEST_BODY, "");
            return chain.filter(exchange);
        }

        // Body 캡처 및 캐싱
        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(dataBuffer -> {
                    try {
                        // DataBuffer 에서 byte array 로 복사
                        int bodySize = dataBuffer.readableByteCount();
                        byte[] bytes = new byte[bodySize];
                        dataBuffer.read(bytes);

                        // Body 문자열 변환
                        String bodyContent = new String(bytes, StandardCharsets.UTF_8);

                        // ExchangeAttributes 에 캐싱 (모든 필터가 공유)
                        exchange.getAttributes().put(ExchangeAttributes.REQUEST_BODY, bodyContent);

                        log.info("Request body cached: {} bytes", bytes.length);

                        // Body 를 다시 읽을 수 있도록 Request 재구성
                        ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(request) {
                            @Override
                            public Flux<DataBuffer> getBody() {
                                return Flux.just(exchange.getResponse().bufferFactory().wrap(bytes));
                            }
                        };

                        // 재구성된 Request 로 Exchange 생성
                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(mutatedRequest)
                                .build();

                        // 다음 필터로 전달
                        return chain.filter(mutatedExchange);

                    } catch (Exception e) {
                        log.error("Failed to cache request body", e);
                        // 에러 발생 시에도 빈 문자열로 캐싱
                        exchange.getAttributes().put(ExchangeAttributes.REQUEST_BODY, "");
                        return chain.filter(exchange);

                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    @Override
    public int getOrder() {
        // 모든 필터 중 가장 먼저 실행 (가장 작은 Order 값)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
