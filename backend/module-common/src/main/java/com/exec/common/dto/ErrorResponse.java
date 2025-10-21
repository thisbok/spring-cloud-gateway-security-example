package com.exec.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("path")
    private String path;

    // 기본 에러 응답 생성
    public static ErrorResponse of(String traceId, String message) {
        return new ErrorResponse(traceId, null, message, LocalDateTime.now(), null);
    }

    // 에러 코드와 함께 에러 응답 생성
    public static ErrorResponse of(String traceId, String errorCode, String message) {
        return new ErrorResponse(traceId, errorCode, message, LocalDateTime.now(), null);
    }

    // 경로 정보와 함께 에러 응답 생성
    public static ErrorResponse of(String traceId, String errorCode, String message, String path) {
        return new ErrorResponse(traceId, errorCode, message, LocalDateTime.now(), path);
    }

    // 완전한 에러 응답 생성
    public static ErrorResponse of(String traceId, String errorCode, String message,
                                   LocalDateTime timestamp, String path) {
        return new ErrorResponse(traceId, errorCode, message, timestamp, path);
    }

    // HTTP 상태별 에러 응답 생성
    public static ErrorResponse unauthorized(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("UNAUTHORIZED")
                .message(message != null ? message : "Unauthorized access")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse forbidden(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("FORBIDDEN")
                .message(message != null ? message : "Access denied")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse notFound(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("NOT_FOUND")
                .message(message != null ? message : "Resource not found")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse badRequest(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("BAD_REQUEST")
                .message(message != null ? message : "Invalid request")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse internalServerError(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message(message != null ? message : "Internal server error")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse serviceUnavailable(String traceId, String message) {
        return ErrorResponse.builder()
                .traceId(traceId)
                .errorCode("SERVICE_UNAVAILABLE")
                .message(message != null ? message : "Service temporarily unavailable")
                .timestamp(LocalDateTime.now())
                .build();
    }
}