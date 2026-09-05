package com.odwambombo.fraudruleengine.shared.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "fraud.rate-limit")
public class ApiRateLimitProperties {

    private boolean enabled = true;

    @Positive
    private long maxClientBuckets = 100_000;

    @NotNull
    private Duration clientIdleTime = Duration.ofMinutes(10);

    @Valid
    private final Limit api = new Limit(1_200, 100);

    @Valid
    private final Limit login = new Limit(10, 3);

    @AssertTrue(message = "rate-limit client idle time must be at least one minute")
    public boolean isClientIdleTimeValid() {
        return clientIdleTime != null
                && clientIdleTime.compareTo(Duration.ofMinutes(1)) >= 0;
    }

    @Getter
    @Setter
    public static class Limit {

        @Positive
        private long requestsPerMinute;

        @Positive
        private long requestsPerSecond;

        Limit(long requestsPerMinute, long requestsPerSecond) {
            this.requestsPerMinute = requestsPerMinute;
            this.requestsPerSecond = requestsPerSecond;
        }

        @AssertTrue(message = "requests per second must not exceed requests per minute")
        public boolean isBurstLimitValid() {
            return requestsPerSecond > 0
                    && requestsPerMinute > 0
                    && requestsPerSecond <= requestsPerMinute;
        }
    }
}
