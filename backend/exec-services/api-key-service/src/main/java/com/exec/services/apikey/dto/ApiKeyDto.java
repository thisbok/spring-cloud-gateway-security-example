package com.exec.services.apikey.dto;

import com.exec.core.domain.ApiKey;
import com.exec.core.enums.ApiKeyStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyDto {
    private Long id;
    private String accessKey;
    private String clientId;
    private String secret; // 보안상 응답에서는 마스킹 처리
    private String description;
    private ApiKeyStatus status;
    private String allowedIps;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    public static ApiKeyDto from(ApiKey apiKey) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .accessKey(apiKey.getAccessKey())
                .secret(apiKey.getSecret())
                .clientId(apiKey.getClientId())
                .description(apiKey.getDescription())
                .status(apiKey.getStatus())
                .allowedIps(apiKey.getAllowedIps())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }

    public static ApiKeyDto fromWithoutSecret(ApiKey apiKey) {
        return ApiKeyDto.builder()
                .id(apiKey.getId())
                .accessKey(apiKey.getAccessKey())
                .clientId(apiKey.getClientId())
                .secret("***HIDDEN***")
                .description(apiKey.getDescription())
                .status(apiKey.getStatus())
                .allowedIps(apiKey.getAllowedIps())
                .createdAt(apiKey.getCreatedAt())
                .updatedAt(apiKey.getUpdatedAt())
                .build();
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) {
            return "***HIDDEN***";
        }
        return secret.substring(0, 4) + "***" + secret.substring(secret.length() - 4);
    }
}