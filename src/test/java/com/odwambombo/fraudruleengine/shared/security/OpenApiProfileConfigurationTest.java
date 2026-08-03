package com.odwambombo.fraudruleengine.shared.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OpenApiProfileConfigurationTest {

    @ParameterizedTest
    @CsvSource({
            "application.properties, false",
            "application-local.properties, true",
            "application-staging.properties, false",
            "application-production.properties, false"
    })
    void onlyLocalProfileEnablesInteractiveApiDocumentation(
            String profileResource,
            String expectedEnabledValue) throws IOException {
        final Properties profileProperties = loadProperties(profileResource);

        assertThat(profileProperties)
                .containsEntry("springdoc.api-docs.enabled", expectedEnabledValue)
                .containsEntry("springdoc.swagger-ui.enabled", expectedEnabledValue);
    }

    private Properties loadProperties(String profileResource) throws IOException {
        final Properties profileProperties = new Properties();
        try (InputStream propertiesStream = getClass()
                .getClassLoader()
                .getResourceAsStream(profileResource)) {
            assertNotNull(propertiesStream, profileResource + " must exist");
            profileProperties.load(propertiesStream);
        }
        return profileProperties;
    }
}
