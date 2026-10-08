package com.regivolley.api.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Reads the token from the {@code Authorization} header only (never the query string or a form body, threat model D-7a), and
 * on a {@link PublicRoutes public route} reads nothing: a stale or garbage header left in the browser must not turn the
 * login or the public page into a 401. Authorization still decides by its own matchers, so a mismatch between the two can
 * only make a request anonymous, never authenticated.
 */
final class HeaderOnlyBearerTokenResolver implements BearerTokenResolver {

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        if (PublicRoutes.permits(request.getMethod(), request.getRequestURI())) {
            return null;
        }
        return delegate.resolve(request);
    }
}
