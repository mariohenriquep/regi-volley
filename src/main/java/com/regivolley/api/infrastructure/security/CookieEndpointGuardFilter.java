package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * CSRF defence of the two endpoints that read the refresh cookie, {@code POST /api/v1/auth/refresh} and {@code /logout}
 * (threat model D-7a). Spring's CSRF protection is off because every other route authenticates with a header; here there is an
 * ambient credential, so on top of {@code SameSite=Strict}:
 * <ul>
 *   <li>the body must be declared {@code application/json}, which an HTML form cannot send and a cross-site script cannot send
 *       without a CORS preflight that this API refuses (415 otherwise);</li>
 *   <li>a request the browser itself marks {@code Sec-Fetch-Site: cross-site} is 403, whatever its Origin;</li>
 *   <li>if the request carries an {@code Origin}, it must be this API's own origin or the configured web origin ({@code
 *       regi-volley.security.web-origin}); anything else, including {@code null}, is 403. A request with no {@code Origin} (not a
 *       browser form post) is let through: browsers send it on every cross-origin POST.</li>
 * </ul>
 * The worst a forged refresh could ever do is rotate a token the attacker cannot read.
 */
final class CookieEndpointGuardFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(CookieEndpointGuardFilter.class);
    /**
     * Matched like Spring MVC matches them (decoded path segments), not by string equality: {@code /api/v1/auth/%72efresh} reaches
     * the same handler as {@code /api/v1/auth/refresh}, so a literal comparison of the raw URI would let it past this guard.
     */
    private static final List<PathPattern> GUARDED = List.of(
            PathPatternParser.defaultInstance.parse("/api/v1/auth/refresh"),
            PathPatternParser.defaultInstance.parse("/api/v1/auth/logout"));

    private final ApiErrorWriter writer;
    private final Set<String> configuredOrigins;

    /** @param configuredOrigins the web origin(s) allowed besides the API's own, for example {@code https://app.example.org} */
    CookieEndpointGuardFilter(ApiErrorWriter writer, Set<String> configuredOrigins) {
        this.writer = writer;
        this.configuredOrigins = Set.copyOf(configuredOrigins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return GUARDED.stream().noneMatch(pattern -> pattern.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isJson(request.getContentType())) {
            writer.write(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, ApiError.ofStatus(415, RequestIds.current()));
            return;
        }
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            // The browser itself says this request was started by another site: no cookie endpoint serves that.
            LOG.info("Cookie endpoint refused: reason=CROSS_SITE_FETCH path={}", request.getRequestURI());
            writer.write(response, HttpServletResponse.SC_FORBIDDEN, ApiError.forbidden(RequestIds.current()));
            return;
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !isAllowed(origin, request)) {
            LOG.info("Cookie endpoint refused: reason=FOREIGN_ORIGIN path={}", request.getRequestURI());
            writer.write(response, HttpServletResponse.SC_FORBIDDEN, ApiError.forbidden(RequestIds.current()));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        return configuredOrigins.contains(origin) || origin.equals(ownOrigin(request));
    }

    /** {@code scheme://host[:port]} of this request, the default port left out as browsers do. */
    static String ownOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort || port <= 0 ? "" : ":" + port);
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
