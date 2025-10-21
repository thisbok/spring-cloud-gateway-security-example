package com.exec.api.gateway.exception;

import com.exec.api.gateway.security.audit.SecurityAuditEvent;

/**
 * 보안 공격 탐지 시 발생하는 예외
 */
public class SecurityAttackDetectedException extends RuntimeException {

    private final SecurityAuditEvent.EventType attackType;
    private final String payload;

    public SecurityAttackDetectedException(SecurityAuditEvent.EventType attackType, String message, String payload) {
        super(message);
        this.attackType = attackType;
        this.payload = payload;
    }

    public SecurityAuditEvent.EventType getAttackType() {
        return attackType;
    }

    public String getPayload() {
        return payload;
    }
}