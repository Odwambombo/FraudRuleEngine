package com.odwambombo.fraudruleengine.shared.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_rate_limit;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "fraud.security.enabled=false",
        "fraud.rate-limit.enabled=true",
        "fraud.rate-limit.api.requests-per-minute=2",
        "fraud.rate-limit.api.requests-per-second=2"
})
@AutoConfigureMockMvc
class ApiRateLimitingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void rejectsExcessApiRequestsWithoutLimitingHealthChecks() throws Exception {
        mockMvc.perform(get("/api/v1/frontend-config"))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiRateLimitingFilter.RATE_LIMIT_LIMIT_HEADER, "2"))
                .andExpect(header().string(ApiRateLimitingFilter.RATE_LIMIT_REMAINING_HEADER, "1"));

        mockMvc.perform(get("/api/v1/frontend-config"))
                .andExpect(status().isOk())
                .andExpect(header().string(ApiRateLimitingFilter.RATE_LIMIT_REMAINING_HEADER, "0"));

        mockMvc.perform(get("/api/v1/frontend-config"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value(
                        "Too many requests; retry after the indicated delay."
                ))
                .andExpect(jsonPath("$.timestamp").exists());

        mockMvc.perform(get("/livez"))
                .andExpect(status().isOk());

        assertThat(meterRegistry.get("fraud.api.rate.limit.rejected")
                .tag("policy", "api")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
