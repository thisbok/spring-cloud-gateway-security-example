package com.exec.api.gateway.exception;

import lombok.Getter;

/**
 * 중복된 요청 예외 (이전 응답 포함)
 */
@Getter
public class DuplicateRequestWithResponseException extends ApiKeyException {

    private final String previousResponse;

    public DuplicateRequestWithResponseException(String message, String previousResponse) {
        super(message);
        this.previousResponse = previousResponse;
    }

    public DuplicateRequestWithResponseException(String message, String previousResponse, Throwable cause) {
        super(message, cause);
        this.previousResponse = previousResponse;
    }
}