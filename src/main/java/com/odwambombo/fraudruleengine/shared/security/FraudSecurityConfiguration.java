package com.odwambombo.fraudruleengine.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class FraudSecurityConfiguration {

    private static final String HEALTH_ENDPOINT = "/actuator/health";
    private static final String LOCAL_PROFILE = "local";

    @Bean(name = "fraudJwtDecoder")
    @ConditionalOnProperty(prefix = "fraud.security", name = "enabled", havingValue = "true")
    @ConditionalOnProperty(prefix = "fraud.security", name = "provider", havingValue = "cognito")
    JwtDecoder fraudJwtDecoder(FraudSecurityProperties securityProperties) {
        return new SupplierJwtDecoder(() -> {
            final NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withIssuerLocation(securityProperties.getIssuerUri())
                    .build();
            decoder.setJwtValidator(jwtValidator(
                    securityProperties.getIssuerUri(),
                    securityProperties.getAudience()
            ));
            return decoder;
        });
    }

    @Bean
    @ConditionalOnProperty(prefix = "fraud.security", name = "enabled", havingValue = "true")
    SecurityFilterChain securedFilterChain(
            HttpSecurity http,
            FraudAuthorizationPolicy authorizationPolicy,
            @Qualifier("fraudJwtDecoder") JwtDecoder fraudJwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AlertingAuthenticationEntryPoint authenticationEntryPoint,
            Environment environment) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/livez",
                                "/readyz",
                                "/error",
                                HEALTH_ENDPOINT,
                                HEALTH_ENDPOINT + "/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/robots.txt",
                                "/api/v1/frontend-config",
                                "/css/**",
                                "/js/**",
                                "/img/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/transaction-events",
                                "/api/v1/transaction-events/**"
                        ).access(authorizationPolicy.authorizationForBusinessPermission(
                                FraudPermission.TRANSACTION_WRITE
                        ))
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/fraud-assessments",
                                "/api/v1/fraud-assessments/**"
                        ).access(authorizationPolicy.authorizationForBusinessPermission(
                                FraudPermission.ASSESSMENT_READ
                        ))
                        .requestMatchers("/actuator/**")
                        .hasAuthority(FraudPermission.OPERATIONS_READ.authority())
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).access(documentationAuthorizationManager(environment))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt
                                .decoder(fraudJwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> documentationAuthorizationManager(
            Environment environment) {
        if (isExactLocalProfile(environment)) {
            return (authentication, requestContext) -> new AuthorizationDecision(true);
        }
        return AuthorityAuthorizationManager.hasAuthority(
                FraudPermission.DOCS_READ.authority()
        );
    }

    static boolean isExactLocalProfile(Environment environment) {
        final String[] activeProfiles = environment.getActiveProfiles();
        return activeProfiles.length == 1 && LOCAL_PROFILE.equals(activeProfiles[0]);
    }

    @Bean
    @ConditionalOnProperty(prefix = "fraud.security", name = "enabled", havingValue = "true")
    JwtAuthenticationConverter jwtAuthenticationConverter(
            FraudSecurityProperties securityProperties) {
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        if (securityProperties.getProvider() == FraudSecurityProperties.Provider.LOCAL) {
            converter.setJwtGrantedAuthoritiesConverter(
                    new LocalGrantedAuthoritiesConverter()
            );
        } else {
            converter.setJwtGrantedAuthoritiesConverter(
                    new CognitoGrantedAuthoritiesConverter(securityProperties)
            );
        }
        return converter;
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "fraud.security",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true
    )
    SecurityFilterChain localCompatibilityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    static OAuth2TokenValidator<Jwt> jwtValidator(String issuerUri, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new CognitoAudienceValidator(audience)
        );
    }
}
