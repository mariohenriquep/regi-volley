package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.identity.ClientAddresses;
import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;

/**
 * The per-IP rate limits of the public routes (threat model D-9), taken before the body is parsed and before any password is
 * hashed: login, refresh, activation and reset confirmation, password-reset requests, registering an association, filing a join request and reading the public page.
 * The key is the address as {@link RateLimiter#addressKey} makes it (IPv6 by /64). Over the limit the answer is 429 with {@code Retry-After}; there is no lock-out, the bucket just refills. The address is the
 * connection's own ({@code getRemoteAddr}): {@code X-Forwarded-For} counts only if the container was told to trust a proxy
 * ({@code server.forward-headers-strategy}, see architecture.md section 11), otherwise any client could pick a fresh bucket by
 * lying. The per-email limits need the body and live in the services. The address is logged truncated.
 */
final class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

    private record Limit(HttpMethod method, PathPattern pattern, RateLimitRule rule) {
        boolean matches(String requestMethod, String path) {
            return method.name().equals(requestMethod) && pattern.matches(PathContainer.parsePath(path));
        }
    }

    private static Limit limit(HttpMethod method, String pattern, RateLimitRule rule) {
        return new Limit(method, PathPatternParser.defaultInstance.parse(pattern), rule);
    }

    private static final List<Limit> LIMITS = List.of(
            limit(HttpMethod.POST, "/api/v1/auth/login", RateLimitRule.LOGIN_IP),
            limit(HttpMethod.POST, "/api/v1/auth/refresh", RateLimitRule.REFRESH_IP),
            limit(HttpMethod.POST, "/api/v1/auth/activate", RateLimitRule.LINK_TOKEN_IP),
            limit(HttpMethod.POST, "/api/v1/auth/password-resets", RateLimitRule.LINK_TOKEN_IP),
            limit(HttpMethod.POST, "/api/v1/auth/password-reset-requests", RateLimitRule.RESET_IP),
            limit(HttpMethod.POST, "/api/v1/public/associations", RateLimitRule.REGISTER_IP),
            limit(HttpMethod.POST, "/api/v1/public/associations/{shortName}/join-requests", RateLimitRule.JOIN_IP),
            limit(HttpMethod.GET, "/api/v1/public/associations/{shortName}", RateLimitRule.PUBLIC_PAGE_IP));

    private final RateLimiter limiter;
    private final ApiErrorWriter writer;

    RateLimitFilter(RateLimiter limiter, ApiErrorWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Limit limit : LIMITS) {
            if (limit.matches(request.getMethod(), request.getRequestURI())) {
                try {
                    limiter.check(limit.rule(), RateLimiter.addressKey(request.getRemoteAddr()));
                } catch (RateLimitExceededException e) {
                    LOG.info("Rate limit hit: rule={} ip={}", limit.rule(), ClientAddresses.truncate(request.getRemoteAddr()));
                    response.setHeader("Retry-After", String.valueOf(e.retryAfterSeconds()));
                    writer.write(response, 429, ApiError.tooManyRequests(RequestIds.current()));
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
