package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.result.SessionTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Threat model D-7a, D-7b: behind a TLS proxy the web origin is configured, and only it (and the API's own) may call the cookie routes. */
@TestPropertySource(properties = "regi-volley.security.web-origin=https://app.example.org")
class ConfiguredWebOriginWebTest extends AbstractSecuredWebTest {

    private MockHttpServletRequestBuilder refreshFrom(String origin, String clientAddress) {
        return post("/api/v1/auth/refresh").with(request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).header("Origin", origin).cookie(new Cookie("__Secure-rt", "r")).content("{}");
    }

    @Test
    void theConfiguredWebOriginIsAccepted() throws Exception {
        // Arrange
        when(refreshSessionUseCase.execute(any(RefreshSessionCommand.class))).thenReturn(new SessionTokens(
                issuer.issue(userId, association.id(), member.id(), STAMP).value(), 600, "next", SecurityFixtures.NOW.plusSeconds(60)));

        // Act
        ResultActions result = mockMvc.perform(refreshFrom("https://app.example.org", "192.0.2.31"));

        // Assert
        result.andExpect(status().isOk());
    }

    @Test
    void anyOtherOriginStillIsNot() throws Exception {
        // Arrange
        MockHttpServletRequestBuilder lookalike = refreshFrom("https://app.example.org.evil.example", "192.0.2.32");

        // Act
        ResultActions result = mockMvc.perform(lookalike);

        // Assert
        result.andExpect(status().isForbidden());
    }
}
