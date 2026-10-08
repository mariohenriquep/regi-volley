package com.regivolley.api.infrastructure.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * The routes that need no token (threat model section 7, rule 1). The filter chain is default-deny
 * ({@code anyRequest().authenticated()}); this list is the only way out of it, and the route-inventory test checks every
 * mapped route against it. A route not listed here is authenticated. The set is fixed by the threat model:
 * registering an association, the public association page, a join request, the credential endpoints (26b) and the
 * {@code /error} dispatch (generic body only).
 */
public final class PublicRoutes {

    /** A route that needs no token. A {@code null} method means any method. */
    public record Route(HttpMethod method, String pattern) {

        boolean matches(String requestMethod, String path) {
            PathPattern parsed = PathPatternParser.defaultInstance.parse(pattern);
            return (method == null || method.name().equals(requestMethod)) && parsed.matches(PathContainer.parsePath(path));
        }
    }

    public static final List<Route> ALL = List.of(
            new Route(HttpMethod.POST, "/api/v1/public/associations"),
            new Route(HttpMethod.GET, "/api/v1/public/associations/{shortName}"),
            new Route(HttpMethod.POST, "/api/v1/public/associations/{shortName}/join-requests"),
            new Route(HttpMethod.POST, "/api/v1/auth/login"),
            new Route(HttpMethod.POST, "/api/v1/auth/refresh"),
            new Route(HttpMethod.POST, "/api/v1/auth/logout"),
            new Route(HttpMethod.POST, "/api/v1/auth/activate"),
            new Route(HttpMethod.POST, "/api/v1/auth/password-reset-requests"),
            new Route(HttpMethod.POST, "/api/v1/auth/password-resets"),
            new Route(null, "/error"));

    private PublicRoutes() {
    }

    /** Whether a request with this method and concrete path needs no token. */
    public static boolean permits(String method, String path) {
        return ALL.stream().anyMatch(route -> route.matches(method, path));
    }
}
