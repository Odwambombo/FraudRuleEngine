package com.odwambombo.fraudruleengine.shared.security;

import com.odwambombo.fraudruleengine.shared.observability.ApplicationAlertSignals;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Records rejected authentication before delegating to the standard bearer response. */
@Component
final class AlertingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApplicationAlertSignals alertSignals;
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    AlertingAuthenticationEntryPoint(ApplicationAlertSignals alertSignals) {
        this.alertSignals = alertSignals;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException) throws IOException, ServletException {
        alertSignals.recordAuthenticationFailure(authenticationException);
        delegate.commence(request, response, authenticationException);
    }
}
