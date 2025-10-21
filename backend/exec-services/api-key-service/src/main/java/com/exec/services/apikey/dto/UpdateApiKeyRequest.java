package com.exec.services.apikey.dto;

import com.exec.core.enums.ApiKeyStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApiKeyRequest {

    private String description;

    private Boolean isLimit;

    private ApiKeyStatus status;

    private Integer rateLimitPerMinute;

    private Integer rateLimitPerHour;

    private Integer rateLimitPerDay;
}