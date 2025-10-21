package com.exec.api.gateway.validator;

import com.exec.api.gateway.security.SecurityLayerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 🕐 Timestamp 검증기
 * <p>
 * SignedDate 유효성 검증을 통한 Replay Attack 방어:
 * 1. ISO 8601 형식 검증
 * 2. 허용 시간 범위 검증 (과거/미래 모두)
 * 3. 시계 동기화 오류 탐지
 * <p>
 * 지원 형식:
 * - 2025-10-14T10:09:51+09:00 (ISO_OFFSET_DATE_TIME)
 * - 2025-10-14T01:09:51Z (ISO_INSTANT)
 * - 2025-10-14T01:09:51.123Z (밀리초 포함)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TimestampValidator {

    private final SecurityLayerConfig securityConfig;

    /**
     * SignedDate 유효성 검증
     *
     * @param signedDate 클라이언트가 전송한 서명 시점 (ISO 8601 형식)
     * @param accessKey  API Key (로깅용)
     * @return 검증 결과
     */
    public ValidationResult validate(String signedDate, String accessKey) {
        if (signedDate == null || signedDate.isEmpty()) {
            log.warn("SignedDate is null or empty for accessKey: {}", accessKey);
            return ValidationResult.invalid("SignedDate is required");
        }

        try {
            // ISO 8601 형식 파싱 (타임존 정보 포함)
            OffsetDateTime clientTime = OffsetDateTime.parse(signedDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            OffsetDateTime serverTime = OffsetDateTime.now();

            // 시간 차이 계산 (절댓값)
            long diffSeconds = Math.abs(ChronoUnit.SECONDS.between(clientTime, serverTime));

            // 허용 범위 조회
            long toleranceSeconds = securityConfig.getAuthentication()
                    .getApiKey()
                    .getTimestampToleranceSeconds();

            // 허용 범위 초과 검증
            if (diffSeconds > toleranceSeconds) {
                log.warn("Timestamp out of tolerance. AccessKey: {}, Diff: {}s, Allowed: {}s, SignedDate: {}",
                        accessKey, diffSeconds, toleranceSeconds, signedDate);

                return ValidationResult.invalid(
                        String.format("Timestamp out of tolerance: %ds (allowed: %ds)",
                                diffSeconds, toleranceSeconds)
                );
            }

            // 시계 동기화 오류 경고 (허용 범위 내이지만 큰 차이)
            if (diffSeconds > toleranceSeconds / 2) {
                log.info("Clock skew detected. AccessKey: {}, Diff: {}s, SignedDate: {}",
                        accessKey, diffSeconds, signedDate);
            }

            log.debug("Timestamp validation passed. AccessKey: {}, Diff: {}s", accessKey, diffSeconds);
            return ValidationResult.valid(diffSeconds);

        } catch (DateTimeParseException e) {
            log.error("Invalid timestamp format. AccessKey: {}, SignedDate: {}, Error: {}",
                    accessKey, signedDate, e.getMessage());
            return ValidationResult.invalid("Invalid timestamp format: " + e.getMessage());
        }
    }

    /**
     * 검증 결과 DTO
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Long clockSkewSeconds;

        private ValidationResult(boolean valid, String errorMessage, Long clockSkewSeconds) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.clockSkewSeconds = clockSkewSeconds;
        }

        public static ValidationResult valid(long clockSkewSeconds) {
            return new ValidationResult(true, null, clockSkewSeconds);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Long getClockSkewSeconds() {
            return clockSkewSeconds;
        }
    }
}
