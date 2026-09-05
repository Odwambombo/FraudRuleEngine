package com.odwambombo.fraudruleengine.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "fraud.rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
class ApiRateLimiter {

    private final ApiRateLimitProperties properties;
    private final Cache<ClientBucketKey, Bucket> clientBuckets;

    ApiRateLimiter(ApiRateLimitProperties properties) {
        this.properties = properties;
        this.clientBuckets = Caffeine.newBuilder()
                .maximumSize(properties.getMaxClientBuckets())
                .expireAfterAccess(properties.getClientIdleTime())
                .build();
    }

    RateLimitDecision tryAcquire(Policy policy, String clientId) {
        final ApiRateLimitProperties.Limit limit = limitFor(policy);
        final Bucket bucket = clientBuckets.get(
                new ClientBucketKey(policy, clientId),
                ignored -> createBucket(limit)
        );
        final ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new RateLimitDecision(
                probe.isConsumed(),
                limit.getRequestsPerMinute(),
                probe.getRemainingTokens(),
                probe.getNanosToWaitForRefill()
        );
    }

    private ApiRateLimitProperties.Limit limitFor(Policy policy) {
        return policy == Policy.LOGIN ? properties.getLogin() : properties.getApi();
    }

    private Bucket createBucket(ApiRateLimitProperties.Limit limit) {
        return Bucket.builder()
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.getRequestsPerMinute())
                        .refillGreedy(
                                limit.getRequestsPerMinute(),
                                Duration.ofMinutes(1)
                        ))
                .addLimit(bandwidth -> bandwidth
                        .capacity(limit.getRequestsPerSecond())
                        .refillGreedy(
                                limit.getRequestsPerSecond(),
                                Duration.ofSeconds(1)
                        ))
                .build();
    }

    enum Policy {
        API("api"),
        LOGIN("login");

        private final String metricTag;

        Policy(String metricTag) {
            this.metricTag = metricTag;
        }

        String metricTag() {
            return metricTag;
        }
    }

    record RateLimitDecision(
            boolean allowed,
            long limit,
            long remaining,
            long nanosToWaitForRefill) {
    }

    private record ClientBucketKey(Policy policy, String clientId) {
    }
}
