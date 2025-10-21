package com.exec.api.gateway.filter;

import com.exec.api.gateway.exception.*;
import com.exec.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 🔧 Gateway 예외 처리 필터
 * <p>
 * 통합: ExceptionHandlingFilter (ResponseErrorHandlingFilter 기능은 제거됨)
 * <p>
 * 책임:
 * 1. Gateway 내부에서 발생한 예외 처리
 * 2. 예외 타입별 HTTP 상태 코드 매핑
 * 3. 표준화된 JSON 에러 응답 생성
 * 4. RequestResponseLoggingFilter 와 협력하여 에러 로깅
 * <p>
 * 중요:
 * - RequestResponseLoggingFilter 의 Response Decorator 를 덮어쓰지 않음
 * - 에러 응답 Body 를 Exchange Attribute 에 저장하여 로깅 가능하도록 함
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnifiedErrorHandlingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Gateway 내부 예외 처리만 수행
        // RequestResponseLoggingFilter 의 Decorator 를 유지하기 위해 추가 Decorator 설정 안 함
        return chain.filter(exchange)
                .onErrorResume(throwable -> handleGatewayException(exchange, throwable));
    }

    /**
     * Gateway 내부에서 발생한 예외 처리
     * (기존 ExceptionHandlingFilter 로직)
     */
    private Mono<Void> handleGatewayException(ServerWebExchange exchange, Throwable throwable) {
        log.error("Error occurred in gateway filter chain", throwable);

        ServerHttpResponse response = exchange.getResponse();

        // 이미 커밋된 응답인지 확인
        if (response.isCommitted()) {
            return Mono.error(throwable);
        }

        // 에러 타입에 따른 처리
        ErrorResponse errorResponse = determineErrorResponse(throwable);

        // HTTP 응답 설정
        response.setStatusCode(HttpStatus.valueOf(errorResponse.httpStatus));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // JSON 응답 직접 생성
        String responseBody = buildErrorJsonResponse(errorResponse.message);
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);

        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        log.warn("Returning error response: status={}, message={}",
                errorResponse.httpStatus, errorResponse.message);

        // RequestResponseLoggingFilter 의 Decorator 를 통해 응답 작성
        // Decorator 가 자동으로 Body 를 캡처하여 Attribute 에 저장함
        return response.writeWith(Mono.just(buffer));
    }


    /**
     * Gateway 예외를 ErrorResponse 로 변환
     */
    private ErrorResponse determineErrorResponse(Throwable throwable) {
        // Gateway 커스텀 예외 타입별 처리
        if (throwable instanceof ApiKeyNotFoundException) {
            return new ErrorResponse(ErrorCode.API_KEY_NOT_FOUND.getHttpStatusCode(),
                    ErrorCode.API_KEY_NOT_FOUND.getMessage());
        }

        if (throwable instanceof ApiKeyExpiredException) {
            return new ErrorResponse(ErrorCode.EXPIRED_TOKEN.getHttpStatusCode(),
                    ErrorCode.EXPIRED_TOKEN.getMessage());
        }

        if (throwable instanceof ApiKeyInactiveException) {
            return new ErrorResponse(ErrorCode.INVALID_API_KEY.getHttpStatusCode(),
                    "API key is not active");
        }

        if (throwable instanceof InvalidApiKeyException) {
            return new ErrorResponse(ErrorCode.INVALID_API_KEY.getHttpStatusCode(),
                    throwable.getMessage());
        }

        if (throwable instanceof MissingApiKeyException) {
            return new ErrorResponse(ErrorCode.MISSING_AUTHENTICATION.getHttpStatusCode(),
                    ErrorCode.MISSING_AUTHENTICATION.getMessage());
        }

        if (throwable instanceof IpNotAllowedException) {
            return new ErrorResponse(ErrorCode.FORBIDDEN_ACCESS.getHttpStatusCode(),
                    throwable.getMessage());
        }

        // 중복 요청 예외 처리
        if (throwable instanceof DuplicateRequestException) {
            return new ErrorResponse(ErrorCode.DUPLICATE_RESOURCE.getHttpStatusCode(),  // HTTP Conflict
                    throwable.getMessage());
        }

        String message = throwable.getMessage();

        // Downstream 서비스에서 반환된 에러 메시지 파싱
        if (message != null) {
            ErrorCode errorCode = mapToErrorCode(message);
            if (errorCode != null) {
                return new ErrorResponse(errorCode.getHttpStatusCode(), errorCode.getMessage());
            }
        }

        // 기본 에러 처리
        return new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatusCode(),
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }


    /**
     * 통합된 ErrorCode 매핑 로직
     * Gateway 예외 및 Downstream 에러 메시지 모두 처리
     */
    private ErrorCode mapToErrorCode(String errorMessage) {
        if (errorMessage == null) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }

        String lowerMessage = errorMessage.toLowerCase();

        // API Key 관련 에러
        if (lowerMessage.contains("api key not found") ||
                lowerMessage.contains("api_key_not_found")) {
            return ErrorCode.API_KEY_NOT_FOUND;
        }

        if (lowerMessage.contains("invalid api key") ||
                lowerMessage.contains("invalid_api_key")) {
            return ErrorCode.INVALID_API_KEY;
        }

        // 토큰 만료
        if (lowerMessage.contains("token has expired") ||
                lowerMessage.contains("expired") ||
                lowerMessage.contains("expired_token")) {
            return ErrorCode.EXPIRED_TOKEN;
        }

        // Rate Limit
        if (lowerMessage.contains("rate limit") ||
                lowerMessage.contains("rate_limit_exceeded")) {
            return ErrorCode.RATE_LIMIT_EXCEEDED;
        }

        // 인증/인가
        if (lowerMessage.contains("unauthorized") ||
                lowerMessage.contains("unauthorized_access")) {
            return ErrorCode.UNAUTHORIZED_ACCESS;
        }

        if (lowerMessage.contains("forbidden") ||
                lowerMessage.contains("forbidden_access")) {
            return ErrorCode.FORBIDDEN_ACCESS;
        }

        // 리소스
        if (lowerMessage.contains("not found") ||
                lowerMessage.contains("resource_not_found")) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }

        // 유효성 검증
        if (lowerMessage.contains("validation") ||
                lowerMessage.contains("validation_failed")) {
            return ErrorCode.VALIDATION_FAILED;
        }

        // 내부 서버 오류
        if (lowerMessage.contains("internal server error") ||
                lowerMessage.contains("internal_server_error")) {
            return ErrorCode.INTERNAL_SERVER_ERROR;
        }

        return null;
    }

    /**
     * JSON 응답을 직접 생성 (ObjectMapper 없이 성능 최적화)
     */
    private String buildErrorJsonResponse(String errorMessage) {
        // JSON 이스케이프 처리
        String escapedMessage = escapeJsonString(errorMessage);
        return "{\"errorMessage\":\"" + escapedMessage + "\"}";
    }

    /**
     * JSON 문자열 이스케이프 처리
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @Override
    public int getOrder() {
        // RequestResponseLoggingFilter 다음에 실행되어야 에러 응답도 캡처 가능
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    /**
     * 에러 응답 내부 클래스
     */
    private static class ErrorResponse {
        final int httpStatus;
        final String message;

        ErrorResponse(int httpStatus, String message) {
            this.httpStatus = httpStatus;
            this.message = message;
        }
    }
}