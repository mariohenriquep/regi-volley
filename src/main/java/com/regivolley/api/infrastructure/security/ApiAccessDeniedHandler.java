package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** The 403 of the filter chain, in the same body shape as every other error. */
class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter writer;

    ApiAccessDeniedHandler(ApiErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writer.write(response, HttpServletResponse.SC_FORBIDDEN, ApiError.forbidden(RequestIds.current()));
    }
}
