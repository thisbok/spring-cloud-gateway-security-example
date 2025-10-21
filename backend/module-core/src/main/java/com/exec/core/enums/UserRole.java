package com.exec.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {

    // Admin roles
    SUPER_ADMIN("Super Administrator", "시스템 전체 관리자"),
    ADMIN("Administrator", "일반 관리자"),
    OPERATOR("Operator", "운영자"),

    // Client roles  
    CLIENT_ADMIN("Client Administrator", "고객사 관리자"),
    CLIENT_USER("Client User", "고객사 사용자"),
    CLIENT_VIEWER("Client Viewer", "고객사 조회자"),

    // System roles
    SYSTEM("System", "시스템 계정"),
    API("API", "API 전용 계정");

    private final String displayName;
    private final String description;

    public boolean isAdminRole() {
        return this == SUPER_ADMIN || this == ADMIN || this == OPERATOR;
    }

    public boolean isClientRole() {
        return this == CLIENT_ADMIN || this == CLIENT_USER || this == CLIENT_VIEWER;
    }

    public boolean isSystemRole() {
        return this == SYSTEM || this == API;
    }

    public boolean hasAdminPrivilege() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    public boolean canManageUsers() {
        return this == SUPER_ADMIN || this == ADMIN || this == CLIENT_ADMIN;
    }

    public boolean canViewReports() {
        return isAdminRole() || isClientRole();
    }

    public boolean canManageApiKeys() {
        return this == SUPER_ADMIN || this == ADMIN || this == CLIENT_ADMIN;
    }
}