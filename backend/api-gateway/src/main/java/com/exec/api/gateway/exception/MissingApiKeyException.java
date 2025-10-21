package com.exec.api.gateway.exception;

public class MissingApiKeyException extends ApiKeyException {
    public MissingApiKeyException(String message) {
        super(message);
    }
}