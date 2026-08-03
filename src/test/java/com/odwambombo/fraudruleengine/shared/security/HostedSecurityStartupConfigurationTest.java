package com.odwambombo.fraudruleengine.shared.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class HostedSecurityStartupConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void rejectsDisabledSecurityForHostedWebApplications(String hostedProfile) {
        hostedWebContextRunner(hostedProfile, false).run(applicationContext -> {
            assertThat(applicationContext).hasFailed();
            assertThat(applicationContext.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage(
                            HostedSecurityStartupConfiguration
                                    .DISABLED_HOSTED_SECURITY_MESSAGE
                    );
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void allowsEnabledSecurityForHostedWebApplications(String hostedProfile) {
        hostedWebContextRunner(hostedProfile, true).run(applicationContext -> {
            assertThat(applicationContext).hasNotFailed();
            assertThat(applicationContext)
                    .hasSingleBean(HostedSecurityStartupConfiguration.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"staging", "production"})
    void allowsDisabledSecurityForNonWebHostedMigrationTask(String hostedProfile) {
        new ApplicationContextRunner()
                .withUserConfiguration(HostedSecurityStartupConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=" + hostedProfile + ",migration"
                )
                .withBean(
                        FraudSecurityProperties.class,
                        () -> securityProperties(false)
                )
                .run(applicationContext -> {
                    assertThat(applicationContext).hasNotFailed();
                    assertThat(applicationContext)
                            .doesNotHaveBean(HostedSecurityStartupConfiguration.class);
                });
    }

    private static WebApplicationContextRunner hostedWebContextRunner(
            String hostedProfile,
            boolean securityEnabled) {
        return new WebApplicationContextRunner()
                .withUserConfiguration(HostedSecurityStartupConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + hostedProfile)
                .withBean(
                        FraudSecurityProperties.class,
                        () -> securityProperties(securityEnabled)
                );
    }

    private static FraudSecurityProperties securityProperties(boolean securityEnabled) {
        final FraudSecurityProperties securityProperties = new FraudSecurityProperties();
        securityProperties.setEnabled(securityEnabled);
        return securityProperties;
    }
}
