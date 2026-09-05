package com.odwambombo.fraudruleengine.shared.ratelimit;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ApiRateLimitingFilterTest {

    private SimpleMeterRegistry meterRegistry;
    private ApiRateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        final ApiRateLimitProperties properties = new ApiRateLimitProperties();
        properties.getApi().setRequestsPerMinute(1);
        properties.getApi().setRequestsPerSecond(1);
        properties.getLogin().setRequestsPerMinute(1);
        properties.getLogin().setRequestsPerSecond(1);
        meterRegistry = new SimpleMeterRegistry();
        filter = new ApiRateLimitingFilter(
                new ApiRateLimiter(properties),
                JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC),
                meterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        meterRegistry.close();
    }

    @Test
    void authenticatedApiRequestsUseThePrincipalAcrossSourceAddresses() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "client-a",
                        "not-used",
                        List.of()
                )
        );
        final FilterChain filterChain = mock(FilterChain.class);

        final MockHttpServletRequest firstRequest = apiRequest("192.0.2.1");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), filterChain);

        final MockHttpServletRequest secondRequest = apiRequest("198.51.100.2");
        final MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, rejectedResponse, filterChain);

        verify(filterChain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        assertThat(rejectedResponse.getStatus()).isEqualTo(429);
        assertThat(rejectedResponse.getHeader("Retry-After")).isNotBlank();
        assertThat(rejectedResponse.getContentAsString())
                .contains("\"code\":\"RATE_LIMIT_EXCEEDED\"")
                .contains("\"timestamp\":\"2026-09-05T10:00:00Z\"");
    }

    @Test
    void loginRequestsUseIndependentPerAddressBuckets() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "client-a",
                        "not-used",
                        List.of()
                )
        );
        final FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(loginRequest("192.0.2.1"), new MockHttpServletResponse(), filterChain);
        filter.doFilter(loginRequest("198.51.100.2"), new MockHttpServletResponse(), filterChain);

        verify(filterChain, times(2)).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private MockHttpServletRequest apiRequest(String remoteAddress) {
        final MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/fraud-assessments"
        );
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private MockHttpServletRequest loginRequest(String remoteAddress) {
        final MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/v1/auth/login"
        );
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
