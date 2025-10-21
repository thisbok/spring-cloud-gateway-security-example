package com.exec.api.gateway.exception;

/**
 * 중복된 요청 예외 (Idempotency Key 중복)
 */
public class DuplicateRequestException extends ApiKeyException {

    public DuplicateRequestException(String message) {
        super(message);
    }

    public DuplicateRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}