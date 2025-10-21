package com.exec.api.gateway.detector;

import com.exec.api.gateway.security.SecurityLayerConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * ⏱️ 시계 동기화 오류 탐지기
 * <p>
 * 클라이언트와 서버의 시간 차이를 측정하여 시스템 시간 문제를 감지합니다.
 * <p>
 * 주요 기능:
 * 1. 클라이언트 시계 skew 측정 (초 단위)
 * 2. 임계값 초과 시 경고 로깅
 * 3. 시계 동기화 문제 조기 발견
 * <p>
 * 권장 사항:
 * - 클라이언트에 NTP 시간 동기화 필수
 * - 임계값: 10 분 (600 초) 초과 시 알림
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClockSkewDetector {

    private static final long CRITICAL_SKEW_SECONDS = 600; // 10 분
    private static final long WARNING_SKEW_SECONDS = 180;  // 3 분

    private final SecurityLayerConfig securityConfig;

    /**
     * 시계 동기화 오류 탐지 및 알림
     *
     * @param accessKey  API Key (로깅용)
     * @param signedDate 클라이언트가 전송한 서명 시점 (ISO 8601 형식)
     * @return 시계 차이 (초 단위) - 양수: 클라이언트 빠름, 음수: 클라이언트 느림
     */
    public ClockSkewResult detectClockSkew(String accessKey, String signedDate) {
        if (signedDate == null || signedDate.isEmpty()) {
            log.debug("SignedDate is missing for accessKey: {}", accessKey);
            return ClockSkewResult.noData();
        }

        try {
            // ISO 8601 형식 파싱 (타임존 정보 포함)
            OffsetDateTime clientTime = OffsetDateTime.parse(signedDate, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            OffsetDateTime serverTime = OffsetDateTime.now();

            // 시간 차이 계산 (양수: 클라이언트 빠름, 음수: 클라이언트 느림)
            long skewSeconds = ChronoUnit.SECONDS.between(serverTime, clientTime);
            long absSkewSeconds = Math.abs(skewSeconds);

            // 임계값에 따른 로깅
            if (absSkewSeconds > CRITICAL_SKEW_SECONDS) {
                log.error("Critical clock skew detected: accessKey={}, skew={}s, signedDate={}",
                        accessKey, skewSeconds, signedDate);
                return ClockSkewResult.critical(skewSeconds, signedDate);
            } else if (absSkewSeconds > WARNING_SKEW_SECONDS) {
                log.warn("Warning clock skew detected: accessKey={}, skew={}s, signedDate={}",
                        accessKey, skewSeconds, signedDate);
                return ClockSkewResult.warning(skewSeconds, signedDate);
            } else {
                log.debug("Clock skew within tolerance: accessKey={}, skew={}s", accessKey, skewSeconds);
                return ClockSkewResult.normal(skewSeconds, signedDate);
            }

        } catch (DateTimeParseException e) {
            log.error("Invalid timestamp format. AccessKey: {}, SignedDate: {}, Error: {}",
                    accessKey, signedDate, e.getMessage());
            return ClockSkewResult.invalidFormat();
        }
    }

    /**
     * 허용 범위 대비 시계 차이 비율 계산
     *
     * @param skewSeconds 시계 차이 (초)
     * @return 허용 범위 대비 비율 (0.0 ~ 1.0+)
     */
    public double calculateSkewRatio(long skewSeconds) {
        long toleranceSeconds = securityConfig.getAuthentication()
                .getApiKey()
                .getTimestampToleranceSeconds();

        return (double) Math.abs(skewSeconds) / toleranceSeconds;
    }

    /**
     * 시계 동기화 권장 여부 판단
     *
     * @param skewSeconds 시계 차이 (초)
     * @return true: 시계 동기화 권장, false: 정상
     */
    public boolean shouldSyncClock(long skewSeconds) {
        return Math.abs(skewSeconds) > WARNING_SKEW_SECONDS;
    }

    /**
     * 시계 차이 심각도
     */
    public enum ClockSkewLevel {
        NORMAL,          // 정상 범위 내
        WARNING,         // 경고 수준 (3 분 초과)
        CRITICAL,        // 위험 수준 (10 분 초과)
        NO_DATA,         // SignedDate 없음
        INVALID_FORMAT   // 잘못된 형식
    }

    /**
     * 시계 차이 탐지 결과
     */
    public static class ClockSkewResult {
        private final ClockSkewLevel level;
        private final Long skewSeconds;
        private final String signedDate;
        private final String message;

        private ClockSkewResult(ClockSkewLevel level, Long skewSeconds, String signedDate, String message) {
            this.level = level;
            this.skewSeconds = skewSeconds;
            this.signedDate = signedDate;
            this.message = message;
        }

        public static ClockSkewResult critical(long skewSeconds, String signedDate) {
            return new ClockSkewResult(
                    ClockSkewLevel.CRITICAL,
                    skewSeconds,
                    signedDate,
                    String.format("Critical clock skew: %ds (threshold: %ds)", skewSeconds, CRITICAL_SKEW_SECONDS)
            );
        }

        public static ClockSkewResult warning(long skewSeconds, String signedDate) {
            return new ClockSkewResult(
                    ClockSkewLevel.WARNING,
                    skewSeconds,
                    signedDate,
                    String.format("Warning clock skew: %ds (threshold: %ds)", skewSeconds, WARNING_SKEW_SECONDS)
            );
        }

        public static ClockSkewResult normal(long skewSeconds, String signedDate) {
            return new ClockSkewResult(
                    ClockSkewLevel.NORMAL,
                    skewSeconds,
                    signedDate,
                    "Clock skew within tolerance"
            );
        }

        public static ClockSkewResult noData() {
            return new ClockSkewResult(
                    ClockSkewLevel.NO_DATA,
                    null,
                    null,
                    "SignedDate not provided"
            );
        }

        public static ClockSkewResult invalidFormat() {
            return new ClockSkewResult(
                    ClockSkewLevel.INVALID_FORMAT,
                    null,
                    null,
                    "Invalid timestamp format"
            );
        }

        public ClockSkewLevel getLevel() {
            return level;
        }

        public Long getSkewSeconds() {
            return skewSeconds;
        }

        public String getSignedDate() {
            return signedDate;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCritical() {
            return level == ClockSkewLevel.CRITICAL;
        }

        public boolean requiresAlert() {
            return level == ClockSkewLevel.CRITICAL || level == ClockSkewLevel.WARNING;
        }
    }
}
