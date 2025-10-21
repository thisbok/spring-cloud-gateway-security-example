package com.exec.api.gateway.filter;

import com.exec.api.gateway.constants.ExchangeAttributes;
import com.exec.api.gateway.exception.SecurityAttackDetectedException;
import com.exec.api.gateway.security.SecurityLayerConfig;
import com.exec.api.gateway.security.audit.SecurityAuditEvent;
import com.exec.api.gateway.security.audit.SecurityAuditService;
import com.exec.api.gateway.util.IpAddressUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 🛡️ 보안 공격 탐지 필터
 * <p>
 * Input Validation 을 통해 다음 공격을 탐지 및 차단:
 * 1. SQL Injection 시도
 * 2. XSS (Cross-Site Scripting) 시도
 * 3. Path Traversal 시도
 * 4. Command Injection 시도
 * 5. 악성 페이로드 탐지
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityAttackDetectionFilter extends AbstractGatewayFilterFactory<SecurityAttackDetectionFilter.Config> {

    // SQL Injection 패턴
    private static final List<Pattern> SQL_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i).*(union|select|insert|update|delete|drop|create|alter|exec|execute)\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*(script|javascript|vbscript|onload|onerror|onclick)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*('|(\\-\\-)|(;)|(\\||\\|)|(\\*|\\*))", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*(and|or)\\s+\\d+\\s*=\\s*\\d+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*\\b(waitfor\\sdelay|benchmark|pg_sleep|sleep)\\b", Pattern.CASE_INSENSITIVE)
    );
    // XSS 패턴
    private static final List<Pattern> XSS_PATTERNS = List.of(
            Pattern.compile("(?i).*<script[^>]*>.*</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*on(load|error|click|focus|blur|change|submit)\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*<object[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*<embed[^>]*>", Pattern.CASE_INSENSITIVE)
    );
    // Path Traversal 패턴
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = List.of(
            Pattern.compile("(?i).*(\\.\\.[\\/\\\\])", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*[\\/\\\\]etc[\\/\\\\]passwd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*[\\/\\\\]windows[\\/\\\\]system32", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*%2e%2e%2f", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*%252e%252e%252f", Pattern.CASE_INSENSITIVE)
    );
    // Command Injection 패턴
    private static final List<Pattern> COMMAND_INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i).*(;|\\||&|\\$|`|\\(|\\)|\\{|\\})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*(cat|ls|ps|whoami|id|uname|wget|curl|nc|netcat)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*(cmd|powershell|bash|sh|zsh)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i).*\\$\\(.*\\)", Pattern.CASE_INSENSITIVE)
    );
    private final SecurityLayerConfig securityConfig;
    private final SecurityAuditService securityAuditService;

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestId = exchange.getAttribute(ExchangeAttributes.REQUEST_ID);
            String ipAddress = IpAddressUtil.getClientIpAddressNormalized(request);

            // Input Validation 이 비활성화된 경우 통과
            if (!securityConfig.getPreValidation().getInputValidation().isSqlInjectionProtection() &&
                    !securityConfig.getPreValidation().getInputValidation().isXssProtection() &&
                    !securityConfig.getPreValidation().getInputValidation().isPathTraversalProtection() &&
                    !securityConfig.getPreValidation().getInputValidation().isCommandInjectionProtection()) {
                return chain.filter(exchange);
            }

            // URI, Query Parameters, Headers 검사
            String uri = request.getURI().toString();
            String queryString = request.getURI().getQuery();
            String userAgent = request.getHeaders().getFirst("User-Agent");

            // 보안 공격 패턴 탐지
            return detectSecurityAttacks(uri, queryString, userAgent)
                    .flatMap(attackDetected -> {
                        if (attackDetected.isAttackDetected()) {
                            handleSecurityAttack(requestId, ipAddress, uri, attackDetected);
                            return Mono.error(new SecurityAttackDetectedException(
                                    attackDetected.getAttackType(),
                                    attackDetected.getDescription(),
                                    attackDetected.getPayload()
                            ));
                        }
                        return chain.filter(exchange);
                    });
        };
    }

    /**
     * 보안 공격 패턴 탐지
     */
    private Mono<AttackDetectionResult> detectSecurityAttacks(String uri, String queryString, String userAgent) {

        var inputValidationConfig = securityConfig.getPreValidation().getInputValidation();

        // SQL Injection 탐지
        if (inputValidationConfig.isSqlInjectionProtection()) {
            AttackDetectionResult sqlResult = detectSqlInjection(uri, queryString);
            if (sqlResult.isAttackDetected()) {
                return Mono.just(sqlResult);
            }
        }

        // XSS 탐지
        if (inputValidationConfig.isXssProtection()) {
            AttackDetectionResult xssResult = detectXSS(uri, queryString, userAgent);
            if (xssResult.isAttackDetected()) {
                return Mono.just(xssResult);
            }
        }

        // Path Traversal 탐지
        if (inputValidationConfig.isPathTraversalProtection()) {
            AttackDetectionResult pathResult = detectPathTraversal(uri, queryString);
            if (pathResult.isAttackDetected()) {
                return Mono.just(pathResult);
            }
        }

        // Command Injection 탐지
        if (inputValidationConfig.isCommandInjectionProtection()) {
            AttackDetectionResult commandResult = detectCommandInjection(uri, queryString);
            if (commandResult.isAttackDetected()) {
                return Mono.just(commandResult);
            }
        }

        return Mono.just(AttackDetectionResult.noAttack());
    }

    /**
     * SQL Injection 탐지
     */
    private AttackDetectionResult detectSqlInjection(String uri, String queryString) {
        String combinedInput = uri + (queryString != null ? "?" + queryString : "");

        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(combinedInput).find()) {
                log.warn("SQL Injection attempt detected in: {}", combinedInput);
                return new AttackDetectionResult(true, SecurityAuditEvent.EventType.SQL_INJECTION_ATTEMPT,
                        "SQL injection pattern detected", combinedInput);
            }
        }
        return AttackDetectionResult.noAttack();
    }

    /**
     * XSS 탐지
     */
    private AttackDetectionResult detectXSS(String uri, String queryString, String userAgent) {
        String combinedInput = uri + (queryString != null ? "?" + queryString : "") +
                (userAgent != null ? " " + userAgent : "");

        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(combinedInput).find()) {
                log.warn("XSS attempt detected in: {}", combinedInput);
                return new AttackDetectionResult(true, SecurityAuditEvent.EventType.XSS_ATTEMPT,
                        "XSS pattern detected", combinedInput);
            }
        }
        return AttackDetectionResult.noAttack();
    }

    /**
     * Path Traversal 탐지
     */
    private AttackDetectionResult detectPathTraversal(String uri, String queryString) {
        String combinedInput = uri + (queryString != null ? "?" + queryString : "");

        for (Pattern pattern : PATH_TRAVERSAL_PATTERNS) {
            if (pattern.matcher(combinedInput).find()) {
                log.warn("Path traversal attempt detected in: {}", combinedInput);
                return new AttackDetectionResult(true, SecurityAuditEvent.EventType.PATH_TRAVERSAL_ATTEMPT,
                        "Path traversal pattern detected", combinedInput);
            }
        }
        return AttackDetectionResult.noAttack();
    }

    /**
     * Command Injection 탐지
     */
    private AttackDetectionResult detectCommandInjection(String uri, String queryString) {
        String combinedInput = uri + (queryString != null ? "?" + queryString : "");

        for (Pattern pattern : COMMAND_INJECTION_PATTERNS) {
            if (pattern.matcher(combinedInput).find()) {
                log.warn("Command injection attempt detected in: {}", combinedInput);
                return new AttackDetectionResult(true, SecurityAuditEvent.EventType.COMMAND_INJECTION_ATTEMPT,
                        "Command injection pattern detected", combinedInput);
            }
        }
        return AttackDetectionResult.noAttack();
    }

    /**
     * 보안 공격 처리 - 감사 이벤트만 발행
     */
    private void handleSecurityAttack(String requestId, String ipAddress, String uri, AttackDetectionResult attackResult) {
        // 보안 감사 이벤트 발행 (비동기)
        securityAuditService.publishSecurityAttackAttempt(
                requestId,
                attackResult.getAttackType(),
                ipAddress,
                uri,
                attackResult.getPayload()
        ).subscribe(
                null,
                error -> log.error("[Security] Failed to publish attack attempt event: requestId={}, ip={}, uri={}",
                        requestId, ipAddress, uri, error)
        );
    }


    /**
     * 공격 탐지 결과
     */
    private static class AttackDetectionResult {
        private final boolean attackDetected;
        private final SecurityAuditEvent.EventType attackType;
        private final String description;
        private final String payload;

        public AttackDetectionResult(boolean attackDetected, SecurityAuditEvent.EventType attackType,
                                     String description, String payload) {
            this.attackDetected = attackDetected;
            this.attackType = attackType;
            this.description = description;
            this.payload = payload;
        }

        public static AttackDetectionResult noAttack() {
            return new AttackDetectionResult(false, null, null, null);
        }

        public boolean isAttackDetected() {
            return attackDetected;
        }

        public SecurityAuditEvent.EventType getAttackType() {
            return attackType;
        }

        public String getDescription() {
            return description;
        }

        public String getPayload() {
            return payload;
        }
    }

    public static class Config {
        // 설정이 필요한 경우 여기에 추가
    }
}