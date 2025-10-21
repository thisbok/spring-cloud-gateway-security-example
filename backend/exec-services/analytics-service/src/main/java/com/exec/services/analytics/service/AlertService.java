package com.exec.services.analytics.service;

import com.exec.services.analytics.config.AnalyticsConfig;
import com.exec.common.dto.ApiGatewayRequestLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 🚨 알림 서비스
 * <p>
 * 실시간 이상 징후 탐지 시 알림 발송
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AnalyticsConfig analyticsConfig;

    /**
     * 에러율 급증 알림
     */
    public void triggerErrorRateAlert(String clientId, double currentErrorRate, double threshold) {
        if (!analyticsConfig.getAlerts().isEnabled()) {
            return;
        }

        String message = String.format(
                "🚨 에러율 급증 알림: Client %s - Error Rate: %.2f%% (임계값: %.2f%%)",
                clientId, currentErrorRate * 100, threshold * 100
        );

        sendAlert("ERROR_RATE_SPIKE", message, "high");
    }

    /**
     * 트래픽 급증 알림
     */
    public void triggerTrafficSpikeAlert(String clientId, long currentRequests, long avgRequests) {
        if (!analyticsConfig.getAlerts().isEnabled()) {
            return;
        }

        String message = String.format(
                "🚨 트래픽 급증 알림: Client %s - Current: %d/min, Avg: %d/min (%.1fx 증가)",
                clientId, currentRequests, avgRequests, (double) currentRequests / avgRequests
        );

        sendAlert("TRAFFIC_SPIKE", message, "medium");
    }

    /**
     * 응답 지연 알림
     */
    public void triggerSlowResponseAlert(ApiGatewayRequestLogDto logDto, long threshold) {
        if (!analyticsConfig.getAlerts().isEnabled()) {
            return;
        }

        String message = String.format(
                "🚨 응답 지연 알림: %s - %dms (임계값: %dms)",
                logDto.getRequestId(), logDto.getResponseTimeMs(), threshold
        );

        sendAlert("SLOW_RESPONSE", message, "low");
    }

    /**
     * 알림 발송 (다중 채널)
     */
    private void sendAlert(String alertType, String message, String severity) {
        try {
            // 로그 채널
            if (analyticsConfig.getAlerts().getChannels().contains("log")) {
                log.warn("[ALERT][{}][{}] {}", alertType, severity.toUpperCase(), message);
            }

            // Webhook 채널
            if (analyticsConfig.getAlerts().getChannels().contains("webhook")) {
                sendWebhookAlert(alertType, message, severity);
            }

            // Slack 채널 (TODO)
            if (analyticsConfig.getAlerts().getChannels().contains("slack")) {
                sendSlackAlert(alertType, message, severity);
            }

            // Email 채널 (TODO)
            if (analyticsConfig.getAlerts().getChannels().contains("email")) {
                sendEmailAlert(alertType, message, severity);
            }

        } catch (Exception e) {
            log.error("알림 발송 실패: {}", message, e);
        }
    }

    /**
     * Webhook 알림 발송
     */
    private void sendWebhookAlert(String alertType, String message, String severity) {
        try {
            // TODO: HTTP 클라이언트를 통한 Webhook 호출
            log.info("Webhook 알림 발송: {} - {}", alertType, message);
        } catch (Exception e) {
            log.error("Webhook 알림 발송 실패", e);
        }
    }

    /**
     * Slack 알림 발송
     */
    private void sendSlackAlert(String alertType, String message, String severity) {
        try {
            // TODO: Slack API 연동
            log.info("Slack 알림 발송: {} - {}", alertType, message);
        } catch (Exception e) {
            log.error("Slack 알림 발송 실패", e);
        }
    }

    /**
     * Email 알림 발송
     */
    private void sendEmailAlert(String alertType, String message, String severity) {
        try {
            // TODO: Email 서비스 연동
            log.info("Email 알림 발송: {} - {}", alertType, message);
        } catch (Exception e) {
            log.error("Email 알림 발송 실패", e);
        }
    }
}