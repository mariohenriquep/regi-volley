package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The authenticated per-user limit (threat model U7, D-9): 300 calls a minute for one user over every route, so a valid account
 * cannot hammer booking or history. It runs right after the bearer token was verified and resolved, so the key is the user id of
 * the {@link AuthenticatedActor}, never anything the client typed; a request without a valid token never gets here and spends no
 * one's budget. Over the limit the answer is the same 429 with {@code Retry-After} as the per-IP limits; the log line names the
 * user by id only.
 */
final class UserRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(UserRateLimitFilter.class);

    private final RateLimiter limiter;
    private final ApiErrorWriter writer;

    UserRateLimitFilter(RateLimiter limiter, ApiErrorWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ActorAuthenticationToken token) {
            String userId = token.getPrincipal().userId().toString();
            try {
                limiter.check(RateLimitRule.USER, userId);
            } catch (RateLimitExceededException e) {
                LOG.info("Rate limit hit: rule={} userId={}", RateLimitRule.USER, userId);
                response.setHeader("Retry-After", String.valueOf(e.retryAfterSeconds()));
                writer.write(response, 429, ApiError.tooManyRequests(RequestIds.current()));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
