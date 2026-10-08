package com.regivolley.api.infrastructure.security;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security headers, request id, size limits and error pages (threat model section 7, "HTTP hygiene"). */
class HttpHygieneTest extends AbstractSecuredWebTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final int MAX_BODY = 64 * 1024;

    @Autowired
    private Environment environment;

    private void assertSecurityHeaders(ResultActions result) throws Exception {
        result.andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void securityHeadersAccompanyASuccess() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/me").header("Authorization", authorization));

        // Assert
        result.andExpect(status().isOk());
        assertSecurityHeaders(result);
    }

    @Test
    void securityHeadersAccompanyA401() throws Exception {
        // Arrange
        String path = "/api/v1/me";

        // Act
        ResultActions result = mockMvc.perform(get(path));

        // Assert
        result.andExpect(status().isUnauthorized());
        assertSecurityHeaders(result);
    }

    @Test
    void securityHeadersAccompanyA404() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/nothing").header("Authorization", authorization));

        // Assert
        result.andExpect(status().isNotFound());
        assertSecurityHeaders(result);
    }

    @Test
    void securityHeadersAccompanyA405() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/me").header("Authorization", authorization));

        // Assert
        result.andExpect(status().isMethodNotAllowed());
        assertSecurityHeaders(result);
    }

    @Test
    void hstsIsSentOnHttpsForOneYearIncludingSubdomains() throws Exception {
        // Arrange
        RequestPostProcessor https = request -> {
            request.setSecure(true);
            request.setScheme("https");
            return request;
        };

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()).with(https));

        // Assert
        result.andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void hstsIsNotSentOnPlainHttp() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/me").header("Authorization", authorization));

        // Assert
        result.andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void theRequestIdIsGeneratedHereAndTheClientsValueIsIgnored() throws Exception {
        // Arrange
        String forged = "forged-id\r\nX-Injected: yes";

        // Act
        ResultActions result = mockMvc.perform(get("/api/v1/me").header("X-Request-Id", forged));

        // Assert
        result.andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(header().string("X-Request-Id", not(forged)));
    }

    @Test
    void theRequestIdInTheBodyIsTheOneInTheHeader() throws Exception {
        // Arrange
        String path = "/api/v1/me";

        // Act
        ResultActions result = mockMvc.perform(get(path));
        String header = result.andReturn().getResponse().getHeader("X-Request-Id");

        // Assert
        result.andExpect(jsonPath("$.requestId").value(header));
    }

    @Test
    void everyRequestGetsItsOwnId() throws Exception {
        // Arrange
        String path = "/api/v1/me";

        // Act
        String first = mockMvc.perform(get(path)).andReturn().getResponse().getHeader("X-Request-Id");
        String second = mockMvc.perform(get(path)).andReturn().getResponse().getHeader("X-Request-Id");

        // Assert
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theErrorDispatchCarriesTheIdOfTheRequestThatFailed() throws Exception {
        // Arrange - the container re-dispatches a failed request to /error after the filters have returned (MDC already cleared)
        RequestPostProcessor errorDispatch = request -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute("jakarta.servlet.error.status_code", 431);
            request.setAttribute("jakarta.servlet.error.request_uri", "/api/v1/me");
            request.setAttribute(RequestIds.ATTRIBUTE, "id-of-the-failed-request");
            return request;
        };

        // Act
        ResultActions result = mockMvc.perform(get("/error").with(errorDispatch));

        // Assert
        result.andExpect(status().is(431))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.requestId").value("id-of-the-failed-request"));
    }

    @Test
    void theErrorPageCalledDirectlyHasNothingToShow() throws Exception {
        // Arrange
        String path = "/error";

        // Act
        ResultActions result = mockMvc.perform(get(path));

        // Assert
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aBodyOverSixtyFourKibIsRefusedWith413EvenWithoutAToken() throws Exception {
        // Arrange
        byte[] tooLarge = new byte[MAX_BODY + 1];

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/me").contentType(MediaType.APPLICATION_JSON).content(tooLarge));

        // Assert
        result.andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
        assertSecurityHeaders(result);
    }

    @Test
    void aBodyOfExactlySixtyFourKibPassesTheFilter() throws Exception {
        // Arrange
        byte[] exactly = new byte[MAX_BODY];

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/me").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(exactly));

        // Assert - it reaches the router, which says 405 for POST on this route
        result.andExpect(status().isMethodNotAllowed());
    }

    @Test
    void aBodyOfUnknownLengthIsRefusedWith411() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(post("/api/v1/me").header("Authorization", authorization).header("Transfer-Encoding", "chunked"));

        // Assert
        result.andExpect(status().is(411)).andExpect(jsonPath("$.code").value("LENGTH_REQUIRED"));
    }

    @Test
    void theWellKnownDiscoveryEndpointOfSpringSecurityIsNotServed() throws Exception {
        // Arrange - spring-security-oauth2-resource-server would answer this to anyone, before authorization
        String path = "/.well-known/oauth-protected-resource";

        // Act
        ResultActions anonymous = mockMvc.perform(get(path));
        ResultActions authenticated = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert
        anonymous.andExpect(status().isNotFound()).andExpect(content().string(not(containsString("resource\""))));
        authenticated.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void errorDetailsAreSwitchedOffAndUploadsAreDisabled() {
        // Arrange
        String[] properties = {"server.error.include-message", "server.error.include-stacktrace",
                "server.error.include-binding-errors", "server.error.include-exception"};

        // Act
        long never = java.util.Arrays.stream(properties).filter(name -> "never".equals(environment.getProperty(name))).count();

        // Assert
        assertThat(never).isEqualTo(properties.length);
        assertThat(environment.getProperty("spring.servlet.multipart.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("server.max-http-request-header-size")).isEqualTo("8KB");
        assertThat(environment.getProperty("server.tomcat.max-swallow-size")).isEqualTo("128KB");
        assertThat(environment.getProperty("spring.mvc.log-request-details")).isEqualTo("false");
        assertThat(environment.getProperty("spring.jackson.deserialization.fail-on-unknown-properties")).isEqualTo("true");
        assertThat(environment.getProperty("logging.level.org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver"))
                .isEqualTo("ERROR");
        assertThat(environment.getProperty("logging.pattern.level")).contains("%X{requestId");
    }

    @Test
    void thereIsNoActuatorOrDocumentationEndpoint() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions health = mockMvc.perform(get("/actuator/health").header("Authorization", authorization));
        ResultActions env = mockMvc.perform(get("/actuator/env").header("Authorization", authorization));
        ResultActions swagger = mockMvc.perform(get("/swagger-ui.html").header("Authorization", authorization));

        // Assert
        health.andExpect(status().isNotFound());
        env.andExpect(status().isNotFound());
        swagger.andExpect(status().isNotFound());
    }
}
