package com.exec.api.gateway.exception;

public class ApiKeyExpiredException extends ApiKeyException {
    public ApiKeyExpiredException(String message) {
        super(message);
    }
}