package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokensTest {

    @Test
    void secretsAre256BitsOfUrlSafeRandomnessAndHashesAreSha256Hex() {
        // Arrange
        String first = SecureTokens.newSecret();
        String second = SecureTokens.newSecret();

        // Act
        String hash = SecureTokens.sha256("abc");

        // Assert
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void theGeneratorAdapterUsesThem() {
        // Arrange
        SecureSecretGenerator generator = new SecureSecretGenerator();

        // Act
        String secret = generator.newSecret();

        // Assert
        assertThat(secret).hasSize(43);
        assertThat(generator.hash("abc")).isEqualTo(SecureTokens.sha256("abc"));
    }
}
