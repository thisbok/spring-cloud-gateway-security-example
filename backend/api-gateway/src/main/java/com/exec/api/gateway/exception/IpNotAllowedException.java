package com.exec.api.gateway.exception;

public class IpNotAllowedException extends ApiKeyException {
    public IpNotAllowedException(String message) {
        super(message);
    }
}