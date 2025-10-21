package com.exec.api.gateway.controller;

import com.exec.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@RestController
@Slf4j
public class FallbackController {

    @RequestMapping("/fallback/api-key-service")
    public Mono<ResponseEntity<ApiResponse<Void>>> apiKeyServiceFallback(ServerWebExchange exchange) {
        log.warn("API Key Service fallback triggered for path: {}",
                exchange.getRequest().getPath().value());

        ApiResponse<Void> response = ApiResponse.error(
                "API Key Service is temporarily unavailable. Please try again later."
        );

        // Circuit Breaker 가 열린 경우 503 Service Unavailable 반환
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    @RequestMapping("/fallback/analytics-service")
    public Mono<ResponseEntity<ApiResponse<Void>>> analyticsServiceFallback(ServerWebExchange exchange) {
        log.warn("Analytics Service fallback triggered for path: {}",
                exchange.getRequest().getPath().value());

        ApiResponse<Void> response = ApiResponse.error(
                "Analytics Service is temporarily unavailable. Please try again later."
        );

        // Circuit Breaker 가 열린 경우 503 Service Unavailable 반환
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

}