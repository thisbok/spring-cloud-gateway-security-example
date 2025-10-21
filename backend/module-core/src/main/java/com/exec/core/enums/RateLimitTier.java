package com.exec.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RateLimitTier {
    BASIC(10, 100, "기본"),        // 10 req/sec, 100 burst
    STANDARD(50, 500, "표준"),     // 50 req/sec, 500 burst
    PREMIUM(200, 1000, "프리미엄"),    // 200 req/sec, 1000 burst
    ENTERPRISE(1000, 5000, "엔터프라이즈"); // 1000 req/sec, 5000 burst

    private final int requestsPerSecond;
    private final int burstCapacity;
    private final String displayName;

    public boolean allowsRequest(int currentRequests) {
        return currentRequests <= requestsPerSecond;
    }

    public boolean allowsBurst(int currentBurst) {
        return currentBurst <= burstCapacity;
    }
}