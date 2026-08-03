package com.odwambombo.fraudruleengine.shared.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;

/** Closes the one-shot migration context after application startup succeeds. */
final class DatabaseMigrationCompletionListener implements ApplicationListener<ApplicationReadyEvent>, Ordered {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DatabaseMigrationCompletionListener.class);

    private final MigrationApplicationExit applicationExit;

    DatabaseMigrationCompletionListener(MigrationApplicationExit applicationExit) {
        this.applicationExit = applicationExit;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        final ConfigurableApplicationContext applicationContext = event.getApplicationContext();
        LOGGER.info("Database migration and persistence validation completed successfully");
        applicationExit.exit(applicationContext);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
