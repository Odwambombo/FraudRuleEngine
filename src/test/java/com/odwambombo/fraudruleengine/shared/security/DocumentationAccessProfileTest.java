package com.odwambombo.fraudruleengine.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationAccessProfileTest {

    @Test
    void onlyAnExactLocalProfileMakesDocumentationPublic() {
        final MockEnvironment localEnvironment = environmentWithProfiles("local");
        final MockEnvironment defaultEnvironment = environmentWithProfiles();
        final MockEnvironment stagingEnvironment = environmentWithProfiles("staging");
        final MockEnvironment mixedEnvironment = environmentWithProfiles(
                "local",
                "production"
        );

        assertThat(FraudSecurityConfiguration.isExactLocalProfile(localEnvironment))
                .isTrue();
        assertThat(FraudSecurityConfiguration.isExactLocalProfile(defaultEnvironment))
                .isFalse();
        assertThat(FraudSecurityConfiguration.isExactLocalProfile(stagingEnvironment))
                .isFalse();
        assertThat(FraudSecurityConfiguration.isExactLocalProfile(mixedEnvironment))
                .isFalse();
    }

    private MockEnvironment environmentWithProfiles(String... activeProfiles) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        return environment;
    }
}
