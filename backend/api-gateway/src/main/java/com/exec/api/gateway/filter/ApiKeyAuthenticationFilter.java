package com.exec.api.gateway.filter;

import com.exec.api.gateway.constants.ExchangeAttributes;
import com.exec.api.gateway.dto.ApiKeyDto;
import com.exec.api.gateway.exception.*;
import com.exec.api.gateway.security.SecurityLayerConfig;
import com.exec.api.gateway.security.audit.SecurityAuditService;
import com.exec.api.gateway.service.ApiKeyCacheService;
import com.exec.api.gateway.service.IdempotencyService;
import com.exec.api.gateway.util.AuthorizationHeaderParser;
import com.exec.api.gateway.util.AuthorizationHeaderParser.AuthCredentials;
import com.exec.api.gateway.util.IpAddressUtil;
import com.exec.api.gateway.validator.TimestampValidator;
import com.exec.common.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 🔐 강화된 API Key 인증 필터
 * <p>
 * 다음 보안 기능을 포함:
 * 1. API Key 검증 및 캐싱
 * 2. HMAC 서명 검증
 * 3. Idempotency Key 검증 (Replay Attack 방지)
 * 4. IP 화이트리스트 검증
 * 5. 다단계 인증 지원
 * 6. 실시간 보안 감사 로깅
 */
@Component
@Slf4j
public class ApiKeyAuthenticationFilter extends AbstractGatewayFilterFactory<ApiKeyAuthenticationFilter.Config> {

    // 헤더 상수
    private static final String AUTHORIZATION_HEADER = "Authorization";


    private final ApiKeyCacheService apiKeyCacheService;
    private final SecurityLayerConfig securityConfig;
    private final SecurityAuditService securityAuditService;
    private final CryptoUtil cryptoUtil;
    private final IdempotencyService idempotencyService;
    private final AuthorizationHeaderParser authHeaderParser;
    private final TimestampValidator timestampValidator;

    public ApiKeyAuthenticationFilter(ApiKeyCacheService apiKeyCacheService,
                                      SecurityLayerConfig securityConfig,
                                      SecurityAuditService securityAuditService,
                                      CryptoUtil cryptoUtil,
                                      IdempotencyService idempotencyService,
                                      AuthorizationHeaderParser authHeaderParser,
                                      TimestampValidator timestampValidator) {
        super(Config.class);
        this.apiKeyCacheService = apiKeyCacheService;
        this.securityConfig = securityConfig;
        this.securityAuditService = securityAuditService;
        this.cryptoUtil = cryptoUtil;
        this.idempotencyService = idempotencyService;
        this.authHeaderParser = authHeaderParser;
        this.timestampValidator = timestampValidator;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String ipAddress = IpAddressUtil.getClientIpAddressNormalized(request);
            // API Key 인증이 비활성화된 경우 통과
            if (!securityConfig.getAuthentication().getApiKey().isEnabled()) {
                log.info("API Key authentication is disabled, allowing request to pass through");
                return chain.filter(exchange);
            }
            // Body 는 RequestBodyCacheFilter 에서 이미 캐싱됨
            // ExchangeAttributes.REQUEST_BODY 에서 가져다 사용
            return validateApiKeyAuthentication(exchange, ipAddress)
                    .flatMap(apiKeyDto -> {
                        // RequestResponseLoggingFilter 에서 사용할 수 있도록 Attribute 에 저장
                        exchange.getAttributes().put(ExchangeAttributes.CLIENT_ID, apiKeyDto.getClientId());
                        exchange.getAttributes().put(ExchangeAttributes.API_KEY_ID, apiKeyDto.getId());

                        // 다음 필터로 전달
                        return chain.filter(exchange);
                    })
                    .onErrorResume(throwable -> {
                        // 인증 실패 시 보안 감사 이벤트 발행
                        String requestId = exchange.getAttribute(ExchangeAttributes.REQUEST_ID);
                        String accessKey = exchange.getAttribute(ExchangeAttributes.ACCESS_KEY);

                        securityAuditService.publishApiKeyValidationFailure(
                                requestId, accessKey, ipAddress, throwable.getMessage()
                        ).subscribe();

                        // 에러 전파
                        return Mono.error(throwable);
                    });
        };
    }

    /**
     * API Key 인증 검증 (Authorization 헤더 방식만 지원)
     */
    private Mono<ApiKeyDto> validateApiKeyAuthentication(ServerWebExchange exchange, String ipAddress) {
        ServerHttpRequest request = exchange.getRequest();

        // Authorization 헤더 확인
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);

        if (!StringUtils.hasText(authHeader)) {
            return Mono.error(new MissingApiKeyException("Authorization header is required"));
        }

        // Authorization 헤더 파싱
        AuthCredentials credentials = authHeaderParser.parseAuthorizationHeader(authHeader);

        if (credentials == null) {
            return Mono.error(new InvalidApiKeyException("Invalid Authorization header format"));
        }

        String accessKey = credentials.getAccessKey();
        String signature = credentials.getSignature();
        String idempotencyKey = credentials.getIdempotencyKey();

        log.debug("Authorization header parsed. AccessKey: {}, HasSignature: {}, IdempotencyKey: {}",
                accessKey, signature != null, idempotencyKey);

        // API Key 필수 체크
        if (!StringUtils.hasText(accessKey)) {
            return Mono.error(new MissingApiKeyException("Access key is missing in Authorization header"));
        }

        // 인증 정보를 Attribute 에 저장 (인증 실패 시에도 로깅을 위해 필요)
        exchange.getAttributes().put(ExchangeAttributes.AUTH_CREDENTIALS, credentials);
        exchange.getAttributes().put(ExchangeAttributes.ACCESS_KEY, accessKey);
        exchange.getAttributes().put(ExchangeAttributes.ALGORITHM, credentials.getAlgorithm());
        exchange.getAttributes().put(ExchangeAttributes.SIGNATURE, credentials.getSignature());
        exchange.getAttributes().put(ExchangeAttributes.SIGNED_DATE, credentials.getSignedDate());
        exchange.getAttributes().put(ExchangeAttributes.IDEMPOTENCY_KEY, idempotencyKey);

        // Timestamp 검증 (Replay Attack 방어)
        String signedDate = credentials.getSignedDate();
        if (StringUtils.hasText(signedDate)) {
            TimestampValidator.ValidationResult validationResult = timestampValidator.validate(signedDate, accessKey);
            if (!validationResult.isValid()) {
                log.warn("Timestamp validation failed: accessKey={}, signedDate={}, error={}",
                        accessKey, signedDate, validationResult.getErrorMessage());
                return Mono.error(new InvalidApiKeyException(validationResult.getErrorMessage()));
            }
            // 시계 동기화 경고 로깅 (TimestampValidator 에서 이미 처리)
        }

        // 서명이 필수인 경우 signature 체크
        if (securityConfig.getAuthentication().getApiKey().isRequireSignature() && !StringUtils.hasText(signature)) {
            return Mono.error(new InvalidApiKeyException("Signature is missing in Authorization header"));
        }

        // Idempotency Key 검증 (필수)
        if (!StringUtils.hasText(idempotencyKey)) {
            return Mono.error(new InvalidApiKeyException("Idempotency key is required"));
        }

        // Idempotency 중복 체크 (가장 먼저 실행 - API Key 검증과 독립적)
        return idempotencyService.checkAndMarkProcessing(accessKey, idempotencyKey)
                .flatMap(result -> {
                    if (!result.isAllowed()) {
                        // 중복 요청 - Idempotency Key 가 이미 사용됨
                        log.info("Duplicate request detected for AccessKey: {} | IdempotencyKey: {}",
                                accessKey, idempotencyKey);
                        return Mono.error(new DuplicateRequestException(
                                "Duplicate request with this idempotency key"));
                    }

                    // Idempotency 체크 통과 후 API Key 조회 및 검증
                    return apiKeyCacheService.findByAccessKey(accessKey);
                })
                .flatMap(apiKeyDto -> {
                    // IP 화이트리스트 검증
                    if (!validateIpWhitelist(apiKeyDto, ipAddress)) {
                        return Mono.error(new IpNotAllowedException("IP address not allowed for this API key"));
                    }

                    // HMAC 서명 검증
                    if (securityConfig.getAuthentication().getApiKey().isRequireSignature() && StringUtils.hasText(signature)) {
                        return validateSignatureAndBody(exchange, apiKeyDto, signature, idempotencyKey)
                                .flatMap(isValid -> {
                                    if (!isValid) {
                                        return Mono.error(new InvalidApiKeyException("Invalid signature"));
                                    }
                                    return Mono.just(apiKeyDto);
                                });
                    }

                    return Mono.just(apiKeyDto);
                })
                .switchIfEmpty(Mono.error(new ApiKeyNotFoundException("API key not found")));
    }

    /**
     * IP 화이트리스트 검증
     * <p>
     * 주의: ipAddress 는 이미 정규화된 주소여야 함
     */
    private boolean validateIpWhitelist(ApiKeyDto apiKeyDto, String ipAddress) {
        // SecurityLayerConfig 에서 통합된 설정 확인
        SecurityLayerConfig.Authentication.APIKey apiKeyConfig = securityConfig.getAuthentication().getApiKey();

        // 두 가지 IP 화이트리스트 설정 모두 확인 (레거시 호환성)
        boolean securityIpWhitelistEnabled = apiKeyConfig.getSecurity().isIpWhitelistEnabled();

        if (!securityIpWhitelistEnabled) {
            return true; // 두 설정 모두 비활성화된 경우 통과
        }

        String allowedIps = apiKeyDto.getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true; // 화이트리스트가 설정되지 않은 경우 통과
        }

        // 와일드카드 체크
        if (allowedIps.contains("*")) {
            return true;
        }

        // 허용된 IP 목록과 비교 (정규화된 주소로 비교)
        String[] allowedIpList = allowedIps.split(",");
        for (String allowedIp : allowedIpList) {
            String normalizedAllowedIp = IpAddressUtil.normalizeIpAddress(allowedIp.trim());
            if (ipAddress.equalsIgnoreCase(normalizedAllowedIp)) {
                return true;
            }
        }

        log.warn("IP not in whitelist: {} | AccessKey: {} | Allowed IPs: {}",
                ipAddress, apiKeyDto.getAccessKey(), allowedIps);

        return false;
    }

    /**
     * 서명 및 Body 검증
     */
    private Mono<Boolean> validateSignatureAndBody(ServerWebExchange exchange, ApiKeyDto apiKeyDto,
                                                   String signature, String idempotencyKey) {
        ServerHttpRequest request = exchange.getRequest();

        // RequestBodyCacheFilter 가 캐싱한 Body 사용
        String body = exchange.getAttribute(ExchangeAttributes.REQUEST_BODY);
        final String finalBody = (body != null) ? body : "";

        return Mono.just(finalBody)
                .map(bodyContent -> {
                    try {
                        // URI 정보 추출 (로깅 및 검증에 사용)
                        String path = request.getURI().getPath();
                        String queryString = request.getURI().getQuery();

                        // Authorization 헤더에서 timestamp 확인
                        AuthCredentials credentials = (AuthCredentials) exchange.getAttributes().get(ExchangeAttributes.AUTH_CREDENTIALS);
                        boolean isValid;

                        if (credentials != null && credentials.getSignedDate() != null) {
                            // 새로운 방식: timestamp 포함 서명 검증
                            // Query String 정규화 (1 회만 수행)
                            String canonicalQuery = cryptoUtil.canonicalizeQueryString(queryString);

                            // Enhanced 서명 검증 메서드 사용 (Body Hash + Query 정규화 + 동적 알고리즘)
                            String algorithm = credentials.getAlgorithm();
                            if (algorithm == null || algorithm.isEmpty()) {
                                algorithm = "HmacSHA256"; // 기본값
                            }

                            // 이미 정규화된 canonicalQuery 로 검증 (정규화 중복 제거)
                            isValid = cryptoUtil.verifyApiRequestSignature(
                                    request.getMethod().name(),
                                    path,
                                    canonicalQuery,
                                    idempotencyKey,
                                    bodyContent,  // finalBody 를 통해 전달됨
                                    credentials.getSignedDate(),
                                    apiKeyDto.getSecret(),
                                    signature,
                                    true,  // useBodyHash = true
                                    algorithm
                            );
                        } else {
                            // Timestamp 가 없으면 서명 검증 실패
                            log.warn("Missing timestamp in Authorization header for AccessKey: {}", apiKeyDto.getAccessKey());
                            isValid = false;
                        }

                        if (!isValid) {
                            // 로그용 전체 URI 구성
                            String fullUri = queryString != null && !queryString.isEmpty()
                                    ? path + "?" + queryString
                                    : path;
                            log.warn("HMAC signature validation failed for AccessKey: {} | Method: {} | URI: {} | IdempotencyKey: {}",
                                    apiKeyDto.getAccessKey(), request.getMethod().name(), fullUri, idempotencyKey);
                        }

                        return isValid;

                    } catch (Exception e) {
                        log.error("Error validating HMAC signature for AccessKey: {}", apiKeyDto.getAccessKey(), e);
                        return false;
                    }
                })
                .onErrorReturn(false);
    }


    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}