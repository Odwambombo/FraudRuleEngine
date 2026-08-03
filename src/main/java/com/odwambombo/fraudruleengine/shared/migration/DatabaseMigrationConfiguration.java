package com.odwambombo.fraudruleengine.shared.migration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Configuration for the one-shot database migration task. */
@Configuration(proxyBeanMethods = false)
@Profile("migration")
class DatabaseMigrationConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MigrationApplicationExit migrationApplicationExit() {
        return ConfigurableApplicationContext::close;
    }

    @Bean
    DatabaseMigrationCompletionListener databaseMigrationCompletionListener(
            MigrationApplicationExit applicationExit) {
        return new DatabaseMigrationCompletionListener(applicationExit);
    }

}
