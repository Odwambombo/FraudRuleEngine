package com.odwambombo.fraudruleengine.shared.security;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "fraud.security", name = "enabled", havingValue = "true")
class OpenApiSecurityConfiguration {

    static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    OpenAPI fraudRuleEngineOpenApi() {
        final SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
        return new OpenAPI()
                .info(new Info()
                        .title("Fraud Rule Engine API")
                        .version("v1")
                        .description("Internal fraud-operations API for evaluating categorized "
                                + "transaction events and investigating persisted assessments."))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, bearerScheme));
    }
}
