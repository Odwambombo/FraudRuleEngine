package com.odwambombo.fraudruleengine.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.odwambombo.fraudruleengine.FraudRuleEngineApplication;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class DatabaseMigrationProfileTest {

    private static final String DATABASE_URL = "jdbc:h2:mem:migration_profile;"
            + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";

    @Test
    void appliesLiquibaseChangelogValidatesJpaAndClosesTheContext() throws Exception {
        final ConfigurableApplicationContext applicationContext =
                new SpringApplicationBuilder(FraudRuleEngineApplication.class)
                        .profiles("migration")
                        .run(
                                "--spring.datasource.url=" + DATABASE_URL,
                                "--spring.datasource.username=sa",
                                "--spring.datasource.password=",
                                "--logging.level.root=ERROR");

        assertThat(applicationContext.isActive()).isFalse();
        assertThat(applicationContext.getEnvironment()
                        .getProperty("spring.main.web-application-type"))
                .isEqualTo("none");
        assertThat(applicationContext.getEnvironment()
                        .getProperty("management.opentelemetry.enabled", Boolean.class))
                .isFalse();

        try (var connection = DriverManager.getConnection(DATABASE_URL, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }
    }

}
