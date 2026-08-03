package com.odwambombo.fraudruleengine.shared.configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Installs Spring Boot's managed OpenTelemetry instance into the Logback bridge. */
@Component
final class OpenTelemetryAppenderInitializer implements InitializingBean {

    private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

    OpenTelemetryAppenderInitializer(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        this.openTelemetryProvider = openTelemetryProvider;
    }

    @Override
    public void afterPropertiesSet() {
        final OpenTelemetry configuredOpenTelemetry = openTelemetryProvider.getIfAvailable();
        if (configuredOpenTelemetry != null) {
            OpenTelemetryAppender.install(configuredOpenTelemetry);
        }
    }
}
