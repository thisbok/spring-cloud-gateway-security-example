package com.exec.services.analytics.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 📊 Analytics 서비스 설정
 */
@Configuration
@ConfigurationProperties(prefix = "analytics")
@Getter
@Setter
public class AnalyticsConfig {

    private Metrics metrics = new Metrics();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Alerts alerts = new Alerts();

    @Getter
    @Setter
    public static class Metrics {
        private Realtime realtime = new Realtime();
        private Thresholds thresholds = new Thresholds();
        private Redis redis = new Redis();

        @Getter
        @Setter
        public static class Realtime {
            private boolean enabled = true;
            private List<String> windowSizes = List.of("1m", "5m", "15m", "1h");
            private Duration cleanupInterval = Duration.ofMinutes(5);
        }

        @Getter
        @Setter
        public static class Thresholds {
            private double errorRate = 0.05;      // 5%
            private long responseTime = 3000;     // 3 초
            private double trafficSpike = 5.0;    // 5 배 증가
        }

        @Getter
        @Setter
        public static class Redis {
            private Duration counterTtl = Duration.ofHours(25);
            private int windowTtlMultiplier = 2;
        }
    }

    @Getter
    @Setter
    public static class Elasticsearch {
        private int batchSize = 100;
        private Duration flushInterval = Duration.ofSeconds(30);
        private String indexPattern = "api-logs-yyyy.MM.dd";
    }

    @Getter
    @Setter
    public static class Alerts {
        private boolean enabled = true;
        private List<String> channels = List.of("log", "webhook");
        private String webhookUrl = "http://localhost:8080/webhooks/alerts";
    }
}