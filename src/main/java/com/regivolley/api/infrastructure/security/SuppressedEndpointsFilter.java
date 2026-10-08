package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Answers 404 to the {@code /.well-known/**} endpoints. Spring Security 7's resource-server support registers a filter that
 * serves {@code /.well-known/oauth-protected-resource} to anyone, before authorization runs and without being a mapped route,
 * so neither default-deny nor the route inventory would see it (and it builds a URL from the request's Host header).
 * Nothing here needs it: the API has no OAuth discovery. Placed ahead of the CORS filter, hence ahead of that one.
 */
class SuppressedEndpointsFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/.well-known/";

    private final ApiErrorWriter writer;

    SuppressedEndpointsFilter(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith(PREFIX)) {
            writer.write(response, HttpServletResponse.SC_NOT_FOUND, ApiError.ofStatus(404, RequestIds.current()));
            return;
        }
        chain.doFilter(request, response);
    }
}
