package com.odwambombo.fraudruleengine.shared.migration;

import org.springframework.context.ConfigurableApplicationContext;

/**
 * Closes a completed one-shot migration application.
 *
 * <p>The indirection keeps migration completion testable without terminating
 * the test JVM.</p>
 */
@FunctionalInterface
interface MigrationApplicationExit {

    void exit(ConfigurableApplicationContext applicationContext);

}
