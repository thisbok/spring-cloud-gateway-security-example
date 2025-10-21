package com.exec.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("path")
    private String path;

    @JsonProperty("validation_errors")
    private List<FieldError> validationErrors;

    public static ValidationErrorResponse of(String requestId, String message, List<FieldError> fieldErrors) {
        return ValidationErrorResponse.builder()
                .requestId(requestId)
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .timestamp(LocalDateTime.now())
                .validationErrors(fieldErrors)
                .build();
    }

    public static ValidationErrorResponse of(String requestId, String message, String path,
                                             List<FieldError> fieldErrors) {
        return ValidationErrorResponse.builder()
                .requestId(requestId)
                .errorCode("VALIDATION_ERROR")
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .validationErrors(fieldErrors)
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        @JsonProperty("field")
        private String field;

        @JsonProperty("rejected_value")
        private Object rejectedValue;

        @JsonProperty("message")
        private String message;

        public static FieldError of(String field, Object rejectedValue, String message) {
            return FieldError.builder()
                    .field(field)
                    .rejectedValue(rejectedValue)
                    .message(message)
                    .build();
        }
    }
}