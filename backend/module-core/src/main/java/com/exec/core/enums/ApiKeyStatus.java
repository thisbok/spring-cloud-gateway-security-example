package com.exec.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiKeyStatus {

    ACTIVE("Active", "활성"),
    SUSPENDED("Suspended", "일시정지"),
    EXPIRED("Expired", "만료"),
    REVOKED("Revoked", "폐지");

    private final String displayName;
    private final String description;

    public boolean isUsable() {
        return this == ACTIVE;
    }

    public boolean isBlocked() {
        return this == SUSPENDED || this == EXPIRED || this == REVOKED;
    }
}