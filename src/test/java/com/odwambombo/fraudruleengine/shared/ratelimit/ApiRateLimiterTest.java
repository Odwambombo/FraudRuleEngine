package com.odwambombo.fraudruleengine.shared.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimiterTest {

    @Test
    void maintainsIndependentLimitsForEachClientAndPolicy() {
        final ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.getApi().setRequestsPerMinute(1);
        properties.getApi().setRequestsPerSecond(1);
        properties.getLogin().setRequestsPerMinute(1);
        properties.getLogin().setRequestsPerSecond(1);
        final ApiRateLimiter rateLimiter = new ApiRateLimiter(properties);

        assertThat(rateLimiter.tryAcquire(ApiRateLimiter.Policy.API, "client-a").allowed())
                .isTrue();
        assertThat(rateLimiter.tryAcquire(ApiRateLimiter.Policy.API, "client-a").allowed())
                .isFalse();
        assertThat(rateLimiter.tryAcquire(ApiRateLimiter.Policy.API, "client-b").allowed())
                .isTrue();
        assertThat(rateLimiter.tryAcquire(ApiRateLimiter.Policy.LOGIN, "client-a").allowed())
                .isTrue();
    }
}
