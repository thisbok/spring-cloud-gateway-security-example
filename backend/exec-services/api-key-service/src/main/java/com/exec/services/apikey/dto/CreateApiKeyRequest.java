package com.exec.services.apikey.dto;

import com.exec.core.enums.RateLimitTier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApiKeyRequest {
    private String clientId;

    private RateLimitTier rateLimitTier;

    private String description;
}