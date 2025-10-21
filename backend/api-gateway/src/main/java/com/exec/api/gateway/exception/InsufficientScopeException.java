package com.exec.api.gateway.exception;

public class InsufficientScopeException extends ApiKeyException {
    public InsufficientScopeException(String message) {
        super(message);
    }
}