package com.exec.core.domain;

import com.exec.core.enums.ApiKeyStatus;
import com.exec.core.enums.RateLimitTier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey extends SoftDeleteEntity {
    @Column(name = "access_key", nullable = false, unique = true, length = 64)
    private String accessKey;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "secret", nullable = false, length = 512)
    private String secret; // 암호화된 Secret Key

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_tier", length = 20)
    private RateLimitTier rateLimitTier;

    @Column(name = "allowed_ips", length = 1000)
    private String allowedIps;


    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status;

    public ApiKey(String accessKey, String secret, String clientId,
                  RateLimitTier rateLimitTier) {
        this.accessKey = accessKey;
        this.secret = secret;
        this.clientId = clientId;
        this.rateLimitTier = rateLimitTier != null ? rateLimitTier : RateLimitTier.BASIC;
        this.status = ApiKeyStatus.ACTIVE;
    }

    public static ApiKey create(String accessKey, String secret, String clientId, RateLimitTier rateLimitTier, String description) {
        ApiKey apiKey = new ApiKey(accessKey, secret, clientId, rateLimitTier);
        apiKey.description = description;
        return apiKey;
    }

    public boolean isActive() {
        return status == ApiKeyStatus.ACTIVE;
    }

    public void activate() {
        this.status = ApiKeyStatus.ACTIVE;
    }

    public void suspend() {
        this.status = ApiKeyStatus.SUSPENDED;
    }


}