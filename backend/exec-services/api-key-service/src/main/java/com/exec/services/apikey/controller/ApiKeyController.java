package com.exec.services.apikey.controller;

import com.exec.services.apikey.dto.ApiKeyDto;
import com.exec.services.apikey.dto.CreateApiKeyRequest;
import com.exec.services.apikey.dto.PageResponse;
import com.exec.services.apikey.dto.UpdateApiKeyRequest;
import com.exec.services.apikey.service.ApiKeyService;
import com.exec.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    /**
     * API 키 생성
     */
    @PostMapping
    public ApiResponse<ApiKeyDto> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request) {

        log.info("Creating API key for client: {}", request.getClientId());

        ApiKeyDto apiKey = apiKeyService.createApiKey(request);

        log.info("API key created successfully");
        return ApiResponse.success(apiKey);
    }

    /**
     * API 키 조회 (ID)
     */
    @GetMapping("/{id}")
    public ApiResponse<ApiKeyDto> getApiKey(@PathVariable Long id) {

        log.debug("Getting API key: id={}", id);

        return apiKeyService.getApiKey(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("API key not found"));
    }

    /**
     * API 키 조회 (Access Key)
     */
    @GetMapping("/access/{accessKey}")
    public ApiResponse<ApiKeyDto> getApiKeyByAccessKey(@PathVariable String accessKey) {

        log.debug("Getting API key by access key: {}", accessKey);

        ApiKeyDto apiKey = apiKeyService.getApiKeyByAccessKey(accessKey);
        return ApiResponse.success(apiKey);
    }

    /**
     * 전체 API 키 목록 조회 (관리자용)
     */
    @GetMapping
    public ApiResponse<PageResponse<ApiKeyDto>> getAllApiKeys(
            @PageableDefault(size = 20) Pageable pageable) {

        log.debug("Getting all API keys");

        return ApiResponse.success(apiKeyService.getAllApiKeys(pageable));
    }

    /**
     * API 키 수정
     */
    @PutMapping("/{id}")
    public ApiResponse<ApiKeyDto> updateApiKey(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApiKeyRequest request) {

        log.info("Updating API key: id={}", id);

        ApiKeyDto updatedApiKey = apiKeyService.updateApiKey(id, request);

        log.info("API key updated successfully: id={}", id);
        return ApiResponse.success(updatedApiKey);
    }

    /**
     * API 키 삭제
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteApiKey(@PathVariable Long id) {

        log.info("Deleting API key: id={}", id);

        apiKeyService.deleteApiKey(id);

        log.info("API key deleted successfully: id={}", id);
        return ApiResponse.success();
    }

    /**
     * API 키 활성화
     */
    @PostMapping("/{id}/activate")
    public ApiResponse<Void> activateApiKey(@PathVariable Long id) {

        log.info("Activating API key: id={}", id);

        apiKeyService.activateApiKey(id);

        return ApiResponse.success();
    }

    /**
     * API 키 비활성화
     */
    @PostMapping("/{id}/suspend")
    public ApiResponse<Void> suspendApiKey(@PathVariable Long id) {

        log.info("Suspending API key: id={}", id);

        apiKeyService.suspendApiKey(id);

        return ApiResponse.success();
    }

}