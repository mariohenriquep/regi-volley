package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Under profile dev with an origin configured, only that origin is answered, with credentials on the auth routes only. */
@ActiveProfiles(value = "dev", inheritProfiles = false)
@TestPropertySource(properties = "regi-volley.security.cors.allowed-origin=http://localhost:5173")
class DevCorsOnTheChainTest extends AbstractSecuredWebTest {

    private static final String ORIGIN = "http://localhost:5173";

    @Test
    void theConfiguredOriginIsAllowedWithoutCredentialsOnApiRoutes() throws Exception {
        // Arrange
        String path = "/api/v1/me";

        // Act
        ResultActions result = mockMvc.perform(options(path).header("Origin", ORIGIN).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", "authorization"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void credentialsAreAllowedOnTheAuthRoutes() throws Exception {
        // Arrange
        String path = "/api/v1/auth/refresh";

        // Act
        ResultActions result = mockMvc.perform(options(path).header("Origin", ORIGIN).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void anotherOriginIsRefused() throws Exception {
        // Arrange
        String origin = "https://evil.example";

        // Act
        ResultActions result = mockMvc.perform(options("/api/v1/me").header("Origin", origin).header("Access-Control-Request-Method", "GET"));

        // Assert
        result.andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
