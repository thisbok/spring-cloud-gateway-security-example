package com.exec.api.gateway.exception;

public class InvalidApiKeyException extends ApiKeyException {
    public InvalidApiKeyException(String message) {
        super(message);
    }

    public InvalidApiKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}