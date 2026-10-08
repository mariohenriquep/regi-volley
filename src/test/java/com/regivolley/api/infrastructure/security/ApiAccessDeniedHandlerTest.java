package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The 403 written from inside the filter chain, where the exception advice is not reached. */
class ApiAccessDeniedHandlerTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(new ApiErrorWriter(json));

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIds.MDC_KEY);
    }

    @Test
    void writesTheSameErrorShapeWithAGenericMessage() throws Exception {
        // Arrange
        MDC.put(RequestIds.MDC_KEY, "request-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException denied = new AccessDeniedException("secret detail for ana.silva@example.com");

        // Act
        handler.handle(new MockHttpServletRequest(), response, denied);

        // Assert
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(body.get("message").asString()).isEqualTo("Access is denied");
        assertThat(body.get("requestId").asString()).isEqualTo("request-1");
        assertThat(response.getContentAsString()).doesNotContain("secret").doesNotContain("ana.silva");
    }

    @Test
    void doesNotWriteOnAResponseThatIsAlreadyCommitted() throws Exception {
        // Arrange
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        // Act
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        // Assert
        assertThat(response.getContentAsString()).isEmpty();
    }
}
