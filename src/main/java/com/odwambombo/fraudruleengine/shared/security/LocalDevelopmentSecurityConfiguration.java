package com.odwambombo.fraudruleengine.shared.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Provides a small self-contained JWT issuer for developer workstations only.
 * The profile expression is a safety boundary: hosted and migration profiles
 * cannot create the login endpoint or its signing beans, even if an environment
 * variable accidentally selects the local provider.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!staging & !production & !migration")
@ConditionalOnProperty(
        prefix = "fraud.security",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnProperty(
        prefix = "fraud.security",
        name = "provider",
        havingValue = "local"
)
@EnableConfigurationProperties(LocalDevelopmentAuthenticationProperties.class)
class LocalDevelopmentSecurityConfiguration {

    @Bean
    SecretKey localDevelopmentJwtSigningKey(
            LocalDevelopmentAuthenticationProperties localAuthenticationProperties) {
        return new SecretKeySpec(
                localAuthenticationProperties.getSigningKey().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Bean
    JwtEncoder localDevelopmentJwtEncoder(SecretKey localDevelopmentJwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(localDevelopmentJwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean(name = "fraudJwtDecoder")
    JwtDecoder localDevelopmentJwtDecoder(
            SecretKey localDevelopmentJwtSigningKey,
            FraudSecurityProperties securityProperties) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(localDevelopmentJwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(FraudSecurityConfiguration.jwtValidator(
                securityProperties.getIssuerUri(),
                securityProperties.getAudience()
        ));
        return decoder;
    }

    @Bean
    LocalDevelopmentAuthenticationService localDevelopmentAuthenticationService(
            LocalDevelopmentAuthenticationProperties localAuthenticationProperties,
            JwtEncoder localDevelopmentJwtEncoder,
            FraudSecurityProperties securityProperties,
            java.time.Clock clock) {
        return new LocalDevelopmentAuthenticationService(
                localAuthenticationProperties,
                localDevelopmentJwtEncoder,
                securityProperties,
                clock
        );
    }
}
