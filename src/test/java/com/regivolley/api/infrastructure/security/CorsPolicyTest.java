package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CORS is off unless profile dev names one local origin (threat model D-7b, section 7). */
class CorsPolicyTest {

    private static final String ORIGIN = "http://localhost:5173";

    private static MockEnvironment profile(String name) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(name);
        return environment;
    }

    private static CorsConfiguration configurationFor(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.setRequestURI(path);
        return source.getCorsConfiguration(request);
    }

    private static IllegalStateException refusal(String origin) {
        Executable act = () -> SecurityConfiguration.corsConfigurationSource(profile("dev"), origin);
        return assertThrows(IllegalStateException.class, act);
    }

    @Test
    void offEvenWhenAnOriginIsConfiguredOutsideDev() {
        // Arrange
        MockEnvironment prod = profile("prod");

        // Act
        CorsConfigurationSource source = SecurityConfiguration.corsConfigurationSource(prod, ORIGIN);

        // Assert
        assertThat(configurationFor(source, "/api/v1/me")).isNull();
    }

    @Test
    void offUnderDevWhenNoOriginIsConfigured() {
        // Arrange
        MockEnvironment dev = profile("dev");

        // Act
        CorsConfigurationSource source = SecurityConfiguration.corsConfigurationSource(dev, "");

        // Assert
        assertThat(configurationFor(source, "/api/v1/me")).isNull();
    }

    @Test
    void underDevOneExplicitOriginWithExplicitMethodsAndHeaders() {
        // Arrange
        MockEnvironment dev = profile("dev");

        // Act
        CorsConfiguration api = configurationFor(SecurityConfiguration.corsConfigurationSource(dev, ORIGIN), "/api/v1/me");

        // Assert
        assertThat(api.getAllowedOrigins()).containsExactly(ORIGIN);
        assertThat(api.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
        assertThat(api.getAllowedMethods()).doesNotContain("*");
        assertThat(api.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void credentialsAreAllowedForTheAuthEndpoints() {
        // Arrange
        CorsConfigurationSource source = SecurityConfiguration.corsConfigurationSource(profile("dev"), ORIGIN);

        // Act
        CorsConfiguration auth = configurationFor(source, "/api/v1/auth/refresh");

        // Assert
        assertThat(auth.getAllowCredentials()).isTrue();
    }

    @Test
    void credentialsAreNotAllowedAnywhereElse() {
        // Arrange
        CorsConfigurationSource source = SecurityConfiguration.corsConfigurationSource(profile("dev"), ORIGIN);

        // Act
        CorsConfiguration other = configurationFor(source, "/api/v1/sessions");

        // Assert
        assertThat(other.getAllowCredentials()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void aWildcardOriginIsRefusedAtStartUp() {
        // Arrange
        String origin = "*";

        // Act
        IllegalStateException e = refusal(origin);

        // Assert
        assertThat(e.getMessage()).contains("one explicit origin");
    }

    @Test
    void severalOriginsAreRefusedAtStartUp() {
        // Arrange
        String origin = ORIGIN + ",http://other.example";

        // Act
        IllegalStateException e = refusal(origin);

        // Assert
        assertThat(e.getMessage()).contains("one explicit origin");
    }

    @Test
    void anOriginWithAPathIsRefusedAtStartUp() {
        // Arrange
        String origin = ORIGIN + "/app";

        // Act
        IllegalStateException e = refusal(origin);

        // Assert
        assertThat(e.getMessage()).contains("one explicit origin");
    }

    @Test
    void anOriginWithoutASchemeIsRefusedAtStartUp() {
        // Arrange
        String origin = "localhost:5173";

        // Act
        IllegalStateException e = refusal(origin);

        // Assert
        assertThat(e.getMessage()).contains("one explicit origin");
    }
}
