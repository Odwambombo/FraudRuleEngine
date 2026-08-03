package com.odwambombo.fraudruleengine.shared.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Accepts a standard OAuth audience or Cognito's access-token client_id claim,
 * while preventing a Cognito ID token from being used as an API access token.
 */
final class CognitoAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "The token is not intended for this API",
            null
    );
    private static final OAuth2Error INVALID_TOKEN_USE = new OAuth2Error(
            "invalid_token",
            "A Cognito access token is required",
            null
    );

    private final String expectedAudience;

    CognitoAudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        final Object tokenUse = token.getClaims().get("token_use");
        if (!"access".equals(tokenUse)) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN_USE);
        }

        final List<String> audiences = token.getAudience();
        if ((audiences != null && audiences.contains(expectedAudience)) || expectedAudience.equals(token.getClaims().get("client_id"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
