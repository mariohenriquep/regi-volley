package com.regivolley.api.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id generated here (threat model E4): put in the MDC for the log lines and error bodies, in a request
 * attribute for the error dispatch (see {@link RequestIds}), and returned as {@code X-Request-Id}. A value sent by the client is ignored, so it can neither forge log lines nor collide with another
 * request's id. First filter of the chain, so even the 401 of a request that never authenticates carries one.
 */
final class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put(RequestIds.MDC_KEY, requestId);
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
