package com.exec.api.gateway.exception;

public class ApiKeyNotFoundException extends ApiKeyException {
    public ApiKeyNotFoundException(String message) {
        super(message);
    }
}