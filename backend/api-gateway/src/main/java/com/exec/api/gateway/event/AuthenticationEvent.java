package com.exec.api.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationEvent {
    private String requestId;
    private String accessKey;
    private String clientId;
    private String clientIp;
    private boolean success;
    private String failureReason;
    private String userAgent;
    private LocalDateTime timestamp;

    public static AuthenticationEvent success(String requestId, String accessKey,
                                              String clientId, String clientIp, String userAgent) {
        return new AuthenticationEvent(
                requestId, accessKey, clientId, clientIp, true, null, userAgent, LocalDateTime.now()
        );
    }

    public static AuthenticationEvent failure(String requestId, String accessKey,
                                              String clientIp, String failureReason, String userAgent) {
        return new AuthenticationEvent(
                requestId, accessKey, null, clientIp, false, failureReason, userAgent, LocalDateTime.now()
        );
    }
}