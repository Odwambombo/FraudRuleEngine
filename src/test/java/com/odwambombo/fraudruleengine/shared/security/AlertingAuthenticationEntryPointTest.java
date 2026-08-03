package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

class AlertingAuthenticationEntryPointTest {

    private SimpleMeterRegistry meterRegistry;
    private AlertingAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        entryPoint = new AlertingAuthenticationEntryPoint(
                new ApplicationAlertSignals(meterRegistry)
        );
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void preservesTheStandardBearer401AndRaisesOneAuthenticationSignal() throws Exception {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest("GET", "/actuator/prometheus"),
                response,
                new InsufficientAuthenticationException("Full authentication is required")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
        assertThat(meterRegistry.get("security.authentication.failures").counter().count())
                .isEqualTo(1.0);
    }
}
