package com.regivolley.api.infrastructure.security;

import com.regivolley.api.infrastructure.web.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an {@link ApiError} straight to the response, for the errors raised inside the filter chain (401, 403, 411, 413),
 * which never reach the {@code @RestControllerAdvice}. Same body shape as every other error.
 */
class ApiErrorWriter {

    private final ObjectMapper mapper;

    ApiErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    void write(HttpServletResponse response, int status, ApiError body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
