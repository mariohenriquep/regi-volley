package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** Production behaviour: no CORS at all, even for a preflight from the dev origin. */
class CorsOnTheChainTest extends AbstractSecuredWebTest {

    @Test
    void aPreflightGetsNoCorsHeaders() throws Exception {
        // Arrange
        String origin = "http://localhost:5173";

        // Act
        ResultActions result = mockMvc.perform(options("/api/v1/me").header("Origin", origin)
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"));

        // Assert
        result.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }
}
