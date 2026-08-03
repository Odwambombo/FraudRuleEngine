package com.odwambombo.fraudruleengine.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

class DatabaseMigrationCompletionListenerTest {

    @Test
    void delegatesApplicationShutdownAfterReadyEvent() {
        final AtomicReference<ConfigurableApplicationContext> exitedContext =
                new AtomicReference<>();
        final DatabaseMigrationCompletionListener listener =
                new DatabaseMigrationCompletionListener(exitedContext::set);
        final ConfigurableApplicationContext applicationContext =
                mock(ConfigurableApplicationContext.class);
        final ApplicationReadyEvent event = new ApplicationReadyEvent(
                new SpringApplication(Object.class),
                new String[0],
                applicationContext,
                Duration.ZERO);

        listener.onApplicationEvent(event);

        assertThat(exitedContext).hasValue(applicationContext);
    }

    @Test
    void runsAfterOtherReadyEventListeners() {
        final DatabaseMigrationCompletionListener listener =
                new DatabaseMigrationCompletionListener(applicationContext -> { });

        assertThat(listener.getOrder()).isEqualTo(Integer.MAX_VALUE);
    }

}
