package com.odwambombo.fraudruleengine.shared.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDevelopmentSecurityProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LocalSecurityComponentScan.class)
            .withPropertyValues(
                    "fraud.security.enabled=true",
                    "fraud.security.provider=local"
            );

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production", "migration"})
    void localTokenIssuerCannotLoadInNonLocalProfiles(String profile) {
        contextRunner
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("fraudJwtDecoder");
                    assertThat(context).doesNotHaveBean(
                            LocalDevelopmentAuthenticationController.class
                    );
                    assertThat(context).doesNotHaveBean(
                            LocalDevelopmentAuthenticationService.class
                    );
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackageClasses = LocalDevelopmentAuthenticationController.class,
            useDefaultFilters = false,
            includeFilters = {
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = LocalDevelopmentSecurityConfiguration.class
                    ),
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = LocalDevelopmentAuthenticationController.class
                    )
            }
    )
    static class LocalSecurityComponentScan {
    }
}
