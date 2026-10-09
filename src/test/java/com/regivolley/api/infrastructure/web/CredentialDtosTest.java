package com.regivolley.api.infrastructure.web;

import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.infrastructure.web.dto.AccessTokenResponse;
import com.regivolley.api.infrastructure.web.dto.ActivateAccountRequest;
import com.regivolley.api.infrastructure.web.dto.ForgotPasswordRequest;
import com.regivolley.api.infrastructure.web.dto.LoginRequest;
import com.regivolley.api.infrastructure.web.dto.ResetPasswordRequest;
import com.regivolley.api.infrastructure.web.mapper.AuthWebMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialDtosTest {

    @Test
    void noCredentialDtoPrintsItsContents() {
        // Arrange
        String secret = "s3cret-value-xyz";

        // Act
        String text = String.join("|", new LoginRequest("ana@example.com", secret).toString(),
                new ActivateAccountRequest(secret, secret).toString(), new ResetPasswordRequest(secret, secret).toString(),
                new ForgotPasswordRequest("ana@example.com").toString(), new AccessTokenResponse(secret, "Bearer", 600).toString());

        // Assert
        assertThat(text).doesNotContain(secret).doesNotContain("ana@example.com");
    }

    @Test
    void theMapperExposesTheAccessTokenAndItsLifetimeButNeverTheRefreshToken() {
        // Arrange
        Instant now = Instant.parse("2026-10-12T09:00:00Z");
        SessionTokens tokens = new SessionTokens("jwt-value", 600, "refresh-value", now.plusSeconds(3600));

        // Act
        AccessTokenResponse response = AuthWebMapper.toResponse(tokens);

        // Assert
        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(600);
        assertThat(response.toString()).doesNotContain("refresh-value");
    }
}
