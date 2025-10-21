package com.exec.api.gateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exec.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Order(-2)
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 에러별 HTTP 상태 코드 및 메시지 설정
        HttpStatus status = determineHttpStatus(ex);
        String errorMessage = determineErrorMessage(ex);
        log.error("Exception occurred: {}", ex.getMessage(), ex);

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            ApiResponse<Object> apiResponse = ApiResponse.error(errorMessage);
            String responseBody = objectMapper.writeValueAsString(apiResponse);

            DataBuffer buffer = response.bufferFactory()
                    .wrap(responseBody.getBytes(StandardCharsets.UTF_8));

            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }

    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof MissingApiKeyException ||
                ex instanceof InvalidApiKeyException ||
                ex instanceof ApiKeyInactiveException ||
                ex instanceof ApiKeyExpiredException) {
            return HttpStatus.UNAUTHORIZED;
        } else if (ex instanceof IpNotAllowedException ||
                ex instanceof InsufficientScopeException) {
            return HttpStatus.FORBIDDEN;
        } else if (ex instanceof ApiKeyNotFoundException ||
                ex instanceof NoResourceFoundException) {
            return HttpStatus.NOT_FOUND;
        } else if (ex instanceof DuplicateRequestException) {
            return HttpStatus.CONFLICT;  // 409 Conflict - 중복 요청
        } else if (ex instanceof SecurityAttackDetectedException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof ServiceUnavailableException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String determineErrorMessage(Throwable ex) {
        if (ex instanceof ApiKeyException) {
            return ex.getMessage();
        } else if (ex instanceof SecurityAttackDetectedException) {
            return "Invalid request format detected";
        } else if (ex instanceof ServiceUnavailableException) {
            return ex.getMessage();
        } else if (ex instanceof NoResourceFoundException) {
            return "Resource not found";
        } else {
            return "Internal server error";
        }
    }


}