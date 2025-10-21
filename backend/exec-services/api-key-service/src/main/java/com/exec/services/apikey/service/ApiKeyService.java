package com.exec.services.apikey.service;

import com.exec.core.domain.ApiKey;
import com.exec.core.repository.ApiKeyRepository;
import com.exec.services.apikey.dto.ApiKeyDto;
import com.exec.services.apikey.dto.CreateApiKeyRequest;
import com.exec.services.apikey.dto.PageResponse;
import com.exec.services.apikey.dto.UpdateApiKeyRequest;
import com.exec.common.exception.BusinessException;
import com.exec.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    @Transactional
    public ApiKeyDto createApiKey(CreateApiKeyRequest request) {
        log.info("Creating API key for client: {}", request.getClientId());

        // Access Key 생성 (Base64 인코딩된 32 자리)
        String accessKey = generateAccessKey();

        // Secret Key 생성 (평문 저장)
        String secretKey = generateSecretKey();

        // API Key 엔티티 생성
        ApiKey apiKey = ApiKey.create(
                accessKey,
                secretKey,
                request.getClientId(),
                request.getRateLimitTier(),
                request.getDescription()
        );

        ApiKey savedApiKey = apiKeyRepository.save(apiKey);

        log.info("API key created successfully: id={}, clientId={}", savedApiKey.getId(), request.getClientId());

        // DTO 생성 시 Secret Key 포함 (생성 시에만)
        return ApiKeyDto.from(savedApiKey);
    }

    public Optional<ApiKeyDto> getApiKey(Long id) {
        return apiKeyRepository.findById(id)
                .map(ApiKeyDto::fromWithoutSecret);
    }

    public ApiKeyDto getApiKeyByAccessKey(String accessKey) {
        // DB 에서 조회
        ApiKey apiKey = apiKeyRepository.findByAccessKey(accessKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        // API Key 유효성 검증
        validateApiKey(apiKey);

        return ApiKeyDto.from(apiKey);
    }

    /**
     * API Key 유효성 검증
     */
    private void validateApiKey(ApiKey apiKey) {
        // 상태 체크
        if (!apiKey.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_API_KEY, "API key is not active");
        }
    }

    public PageResponse<ApiKeyDto> getAllApiKeys(Pageable pageable) {
        return PageResponse.of(apiKeyRepository.findAll(pageable)
                .map(ApiKeyDto::fromWithoutSecret));
    }

    @Transactional
    public ApiKeyDto updateApiKey(Long id, UpdateApiKeyRequest request) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        log.info("Updating API key: id={}", id);

        // 필드 업데이트
        if (request.getDescription() != null) {
            apiKey.setDescription(request.getDescription());
        }

        if (request.getStatus() != null) {
            apiKey.setStatus(request.getStatus());
        }

        ApiKey updatedApiKey = apiKeyRepository.save(apiKey);

        log.info("API key updated successfully: id={}", id);

        return ApiKeyDto.fromWithoutSecret(updatedApiKey);
    }

    @Transactional
    public void deleteApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        log.info("Deleting API key: id={}", id);

        // 소프트 삭제
        apiKey.softDelete();
        apiKeyRepository.save(apiKey);

        log.info("API key deleted successfully: id={}", id);
    }

    @Transactional
    public void activateApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        apiKey.activate();
        apiKeyRepository.save(apiKey);

        log.info("API key activated: id={}", id);
    }

    @Transactional
    public void suspendApiKey(Long id) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        apiKey.suspend();
        apiKeyRepository.save(apiKey);

        log.info("API key suspended: id={}", id);
    }

    private String generateAccessKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32]; // 32 bytes = 256 bits
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String generateSecretKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[64]; // 64 bytes = 512 bits
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    /**
     * 인증 검증용 API Key 조회 (Secret 평문 반환)
     * <p>
     * ⚠️ 보안 주의: 이 메서드는 내부 서비스에서만 사용해야 합니다.
     * Secret Key 가 평문으로 반환되므로 로깅이나 외부 노출에 주의해야 합니다.
     *
     * @param accessKey Access Key
     * @return Secret Key 가 평문으로 포함된 API Key DTO
     */
    public ApiKeyDto getApiKeyWithSecretForAuth(String accessKey) {
        // DB 에서 조회 (캐시에는 Secret 을 저장하지 않음)
        ApiKey apiKey = apiKeyRepository.findByAccessKey(accessKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_KEY_NOT_FOUND));

        // API Key 유효성 검증
        validateApiKey(apiKey);

        // DTO 생성 시 Secret 포함 (이미 평문으로 저장됨)
        ApiKeyDto result = ApiKeyDto.from(apiKey);

        log.debug("API key retrieved for auth: accessKey masked");

        return result;
    }
}