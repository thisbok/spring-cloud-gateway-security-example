package com.exec.api.gateway.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ApiCallEvent {
    private String requestId;
    private String clientId;
    private String apiKeyId;
    private String accessKey;
    private String method;
    private String uri;
    private int statusCode;
    private long responseTimeMs;
    private LocalDateTime timestamp;
    private String userAgent;
    private String clientIp;
    private boolean isLimit;

    public static ApiCallEvent create(String requestId, String clientId, String apiKeyId,
                                      String accessKey, String method, String uri,
                                      int statusCode, long responseTimeMs, String userAgent,
                                      String clientIp, boolean isLimit) {
        return new ApiCallEvent(
                requestId, clientId, apiKeyId, accessKey, method, uri,
                statusCode, responseTimeMs, LocalDateTime.now(),
                userAgent, clientIp, isLimit
        );
    }
}