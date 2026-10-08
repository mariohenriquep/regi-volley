package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * The answer to a missing, invalid, expired or revoked token (threat model U6): the same 401 body every time and
 * {@code WWW-Authenticate: Bearer} with no {@code error_description}, so the response says nothing about why.
 */
class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter writer;

    ApiAuthenticationEntryPoint(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, ApiError.unauthenticated(RequestIds.current()));
    }
}
