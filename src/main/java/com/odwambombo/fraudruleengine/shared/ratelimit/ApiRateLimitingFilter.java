package com.odwambombo.fraudruleengine.shared.ratelimit;

import com.odwambombo.fraudruleengine.shared.exception.ApiErrorResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "fraud.rate-limit",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
class ApiRateLimitingFilter extends OncePerRequestFilter {

    static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRateLimitingFilter.class);
    private static final String API_PREFIX = "/api/v1/";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final ApiRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter apiRejectedCounter;
    private final Counter loginRejectedCounter;

    ApiRateLimitingFilter(
            ApiRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.apiRejectedCounter = rejectedCounter(meterRegistry, ApiRateLimiter.Policy.API);
        this.loginRejectedCounter = rejectedCounter(meterRegistry, ApiRateLimiter.Policy.LOGIN);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !requestPath(request).startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        final ApiRateLimiter.Policy policy = policyFor(request);
        final ApiRateLimiter.RateLimitDecision decision = rateLimiter.tryAcquire(
                policy,
                resolveClientId(request, policy)
        );
        response.setHeader(RATE_LIMIT_LIMIT_HEADER, Long.toString(decision.limit()));
        response.setHeader(
                RATE_LIMIT_REMAINING_HEADER,
                Long.toString(decision.remaining())
        );

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        recordRejection(policy, request.getMethod());
        final long retryAfterSeconds = Math.max(
                1,
                TimeUnit.NANOSECONDS.toSeconds(decision.nanosToWaitForRefill() - 1) + 1
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                new ApiErrorResponse(
                        "RATE_LIMIT_EXCEEDED",
                        "Too many requests; retry after the indicated delay.",
                        Instant.now(clock),
                        Map.of()
                )
        );
    }

    private ApiRateLimiter.Policy policyFor(HttpServletRequest request) {
        return LOGIN_PATH.equals(requestPath(request))
                ? ApiRateLimiter.Policy.LOGIN
                : ApiRateLimiter.Policy.API;
    }

    private String requestPath(HttpServletRequest request) {
        final String requestUri = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String resolveClientId(
            HttpServletRequest request,
            ApiRateLimiter.Policy policy) {
        if (policy != ApiRateLimiter.Policy.LOGIN) {
            final Authentication authentication = SecurityContextHolder.getContext()
                    .getAuthentication();
            if (authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    && StringUtils.hasText(authentication.getName())) {
                return "principal:" + authentication.getName();
            }
        }
        final String remoteAddress = request.getRemoteAddr();
        return "address:" + (StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown");
    }

    private void recordRejection(ApiRateLimiter.Policy policy, String requestMethod) {
        if (policy == ApiRateLimiter.Policy.LOGIN) {
            loginRejectedCounter.increment();
        } else {
            apiRejectedCounter.increment();
        }
        LOGGER.atWarn()
                .addKeyValue("rate_limit.policy", policy.metricTag())
                .addKeyValue("http.request.method", requestMethod)
                .addKeyValue("http.response.status_code", HttpStatus.TOO_MANY_REQUESTS.value())
                .log("API rate limit exceeded");
    }

    private Counter rejectedCounter(
            MeterRegistry meterRegistry,
            ApiRateLimiter.Policy policy) {
        return Counter.builder("fraud.api.rate.limit.rejected")
                .description("API requests rejected by the in-process rate limiter")
                .tag("policy", policy.metricTag())
                .register(meterRegistry);
    }
}
