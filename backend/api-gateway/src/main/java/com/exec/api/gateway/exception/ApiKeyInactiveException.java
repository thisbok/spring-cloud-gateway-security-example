package com.exec.api.gateway.exception;

public class ApiKeyInactiveException extends ApiKeyException {
    public ApiKeyInactiveException(String message) {
        super(message);
    }
}