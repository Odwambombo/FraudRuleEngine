package com.odwambombo.fraudruleengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@SpringBootTest(properties = {
        "spring.datasource.url="
                + "jdbc:h2:mem:fraud_rule_engine_context;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "fraud.security.enabled=false"
})
class FraudRuleEngineApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextLoadsWithoutCreatingAwsSecretsManagerClients() {
        assertThat(applicationContext.getEnvironment()
                .getProperty("spring.cloud.aws.secretsmanager.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.getBeansOfType(SecretsManagerClient.class)).isEmpty();
    }

}
