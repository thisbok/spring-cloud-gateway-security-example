package com.exec.api.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiKeyDto {
    private Long id;

    private String clientId;

    private String accessKey;

    private String secret; // 암호화된 Secret Key

    private String status;

    private String allowedIps; // IP 화이트리스트

    private String createdAt;

    private String updatedAt;
}