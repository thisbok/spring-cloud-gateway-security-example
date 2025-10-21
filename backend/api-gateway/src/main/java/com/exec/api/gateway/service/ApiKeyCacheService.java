package com.exec.api.gateway.service;

import com.exec.api.gateway.dto.ApiKeyDto;
import com.exec.api.gateway.exception.ApiKeyNotFoundException;
import com.exec.api.gateway.security.SecurityLayerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.exec.common.dto.ApiResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class ApiKeyCacheService {

    private static final String API_KEY_CACHE_PREFIX = "api_key:";

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityLayerConfig securityConfig;
    private WebClient webClient;
    @Value("${services.api-key-service.url:http://localhost:18081}")
    private String apiKeyServiceUrl;

    public ApiKeyCacheService(ReactiveRedisTemplate<String, Object> reactiveRedisTemplate,
                              ObjectMapper objectMapper,
                              SecurityLayerConfig securityConfig) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
        this.objectMapper = objectMapper;
        this.securityConfig = securityConfig;
    }

    @PostConstruct
    public void initWebClient() {
        this.webClient = WebClient.builder()
                .baseUrl(apiKeyServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Mono<ApiKeyDto> findByAccessKey(String accessKey) {

        String cacheKey = API_KEY_CACHE_PREFIX + accessKey;

        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .map(this::convertToApiKeyDto)
                .switchIfEmpty(
                        // 캐시 미스 시 API Key 서비스에서 조회
                        loadFromApiKeyService(accessKey)
                                .flatMap(apiKey ->
                                        // 캐시에 저장 후 반환 (SecurityLayerConfig 의 timestamp-tolerance-seconds 값 사용)
                                        reactiveRedisTemplate.opsForValue()
                                                .set(cacheKey, apiKey, Duration.ofSeconds(
                                                        securityConfig.getAuthentication().getApiKey().getTimestampToleranceSeconds()))
                                                .then(Mono.just(apiKey))
                                )
                )
                .doOnNext(apiKey -> {
                    log.debug("API key found for access key: {}", accessKey);
                    log.debug("Client ID: {}, API Key ID: {}", apiKey.getClientId(), apiKey.getId());
                })
                .doOnError(error -> {
                    log.error("Failed to find API key for access key: {}", accessKey, error);
                });
    }

    // API Key 서비스에서 조회
    private Mono<ApiKeyDto> loadFromApiKeyService(String accessKey) {
        return webClient.get()
                .uri("/api/v1/api-keys/access/{accessKey}", accessKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("API Key Service returned error status: {} for access key: {}", response.statusCode(), accessKey);
                    return response.bodyToMono(String.class)
                            .doOnNext(errorBody -> log.error("Error response body: {}", errorBody))
                            .then(Mono.error(new ApiKeyNotFoundException("API Key Service error: " + response.statusCode())));
                })
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<ApiKeyDto>>() {
                })
                .doOnNext(response -> log.info("Successfully received response from API Key Service for access key: {}", accessKey))
                .flatMap(response -> {
                    ApiKeyDto data = response.getData();
                    if (data != null) {
                        log.info("API Key Service returned data for access key: {}", accessKey);
                        return Mono.just(data);
                    }

                    String errorMessage = response.getErrorMessage();
                    if (errorMessage != null) {
                        log.warn("API Key Service returned error for access key {}: {}", accessKey, errorMessage);
                        return Mono.error(new ApiKeyNotFoundException(errorMessage));
                    }

                    log.warn("API Key Service returned null data for access key: {}", accessKey);
                    return Mono.empty();
                })
                .doOnError(error -> {
                    log.warn("Failed to load API key from service: {}", accessKey, error);
                });
    }

    private ApiKeyDto convertToApiKeyDto(Object data) {
        if (data == null) {
            return null;
        }

        try {
            // 이미 ApiKeyDto 인 경우
            if (data instanceof ApiKeyDto) {
                return (ApiKeyDto) data;
            }

            // Redis 에서 가져온 데이터를 ObjectMapper 로 변환
            if (data instanceof Map) {
                return objectMapper.convertValue(data, ApiKeyDto.class);
            }

            log.warn("Unknown data type for conversion: {}", data.getClass().getName());
            return null;

        } catch (Exception e) {
            log.error("Failed to convert data to ApiKeyDto: {}", data, e);
            return null;
        }
    }

}