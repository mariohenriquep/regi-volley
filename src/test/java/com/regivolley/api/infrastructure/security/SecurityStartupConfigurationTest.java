package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AccessTokenIssuer;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.regivolley.api.infrastructure.config.StartupGuardConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fail-fast start-up checks (threat model D-2, D-3, section 10.12): outside profiles {@code dev} and {@code test} the
 * application refuses to boot on a missing, non-P-256, kid-less or mismatched signing key, or on the default database
 * password; the ephemeral key exists only under {@code dev} and {@code test}, and never when {@code prod} is active too.
 */
class SecurityStartupConfigurationTest {

    private static final String SIGNING = "regi-volley.security.jwt.signing-key=";
    private static final String VERIFICATION = "regi-volley.security.jwt.verification-keys=";
    private static final String GOOD_DB_PASSWORD = "spring.datasource.password=a-long-random-secret";
    private static final String DEFAULT_DB_PASSWORD = "spring.datasource.password=regi_volley";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(UserConfigurations.of(JwtKeyConfiguration.class, StartupGuardConfiguration.class))
            .withBean(Clock.class, Clock::systemUTC)
            // The mail guard (issue #40) lives in the same configuration: a complete mail setup keeps these tests about keys and passwords.
            .withPropertyValues("regi-volley.mail.host=smtp.example.org", "regi-volley.mail.username=mailer",
                    "regi-volley.mail.password=a-long-random-smtp-secret", "regi-volley.mail.from=no-reply@example.org",
                    "regi-volley.security.web-origin=https://app.example.org");

    private ApplicationContextRunner withProfiles(String profiles, String... properties) {
        return runner.withPropertyValues("spring.profiles.active=" + profiles).withPropertyValues(properties);
    }

    private static String signing(ECKey key) {
        return SIGNING + TestKeys.privateJwk(key);
    }

    private static String verification(ECKey... keys) {
        return VERIFICATION + TestKeys.verificationSet(keys);
    }

    @Test
    void prodStartsWithAValidKeyPairAndARealDatabasePassword() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner prod = withProfiles("prod", signing(key), verification(key), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtKeySet.class).ephemeral()).isFalse();
            assertThat(context).hasSingleBean(JwtDecoder.class).hasSingleBean(AccessTokenIssuer.class);
        });
    }

    @Test
    void prodRefusesToStartWithoutASigningKey() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner prod = withProfiles("prod", verification(key), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY");
        });
    }

    @Test
    void prodRefusesANonP256SigningKey() {
        // Arrange
        ECKey p384 = TestKeys.generate("k1", Curve.P_384);
        ApplicationContextRunner prod = withProfiles("prod", signing(p384), verification(p384), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("P-256");
        });
    }

    @Test
    void prodRefusesASigningKeyWithoutKid() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        String withoutKid = SIGNING + new ECKey.Builder(key).keyID(null).build().toJSONString();
        ApplicationContextRunner prod = withProfiles("prod", withoutKid, verification(key), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("kid");
        });
    }

    @Test
    void prodRefusesASigningKeyThatMatchesNoVerificationKey() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ECKey other = TestKeys.generate("k2");
        ApplicationContextRunner prod = withProfiles("prod", signing(key), verification(other), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_VERIFICATION_KEYS");
        });
    }

    @Test
    void prodRefusesDuplicateVerificationKeys() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner prod = withProfiles("prod", signing(key), verification(key, key), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("duplicate");
        });
    }

    @Test
    void prodRefusesTheDefaultDatabasePassword() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner prod = withProfiles("prod", signing(key), verification(key), DEFAULT_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("DB_PASSWORD");
        });
    }

    @Test
    void prodRefusesAMissingDatabasePassword() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner prod = withProfiles("prod", signing(key), verification(key));

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("DB_PASSWORD");
        });
    }

    @Test
    void prodNeverFallsBackToAnEphemeralKey() {
        // Arrange
        ApplicationContextRunner prod = withProfiles("prod", GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY");
        });
    }

    @Test
    void combiningProdWithDevDoesNotEnableTheEphemeralKey() {
        // Arrange
        ApplicationContextRunner both = withProfiles("prod,dev", GOOD_DB_PASSWORD);

        // Act
        both.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY");
        });
    }

    @Test
    void combiningProdWithTestDoesNotEnableTheEphemeralKeyOrTheDefaultPassword() {
        // Arrange
        ApplicationContextRunner both = withProfiles("prod,test", DEFAULT_DB_PASSWORD);

        // Act
        both.run(context -> {
            // Assert
            assertThat(context).hasFailed();
        });
    }

    @Test
    void devGeneratesAnEphemeralKeyWhenNothingIsConfigured() {
        // Arrange
        ApplicationContextRunner dev = withProfiles("dev");

        // Act
        dev.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtKeySet.class).ephemeral()).isTrue();
            assertThat(context.getBean(JwtKeySet.class).signingKey().getKeyID()).startsWith("dev-");
        });
    }

    @Test
    void theTestProfileGetsAnEphemeralKeyToo() {
        // Arrange
        ApplicationContextRunner test = withProfiles("test");

        // Act
        test.run(context -> {
            // Assert
            assertThat(context.getBean(JwtKeySet.class).ephemeral()).isTrue();
        });
    }

    @Test
    void anyOtherProfileHasNoEphemeralFallback() {
        // Arrange
        ApplicationContextRunner staging = withProfiles("staging", GOOD_DB_PASSWORD);

        // Act
        staging.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY");
        });
    }

    @Test
    void noProfileAtAllHasNoEphemeralFallbackEither() {
        // Arrange
        ApplicationContextRunner none = runner.withPropertyValues(GOOD_DB_PASSWORD);

        // Act
        none.run(context -> {
            // Assert
            assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY");
        });
    }

    @Test
    void theDefaultDatabasePasswordIsRefusedOutsideDevAndTestNotJustInProd() {
        // Arrange
        ECKey key = TestKeys.generate("k1");
        ApplicationContextRunner staging = withProfiles("staging", signing(key), verification(key), DEFAULT_DB_PASSWORD);
        ApplicationContextRunner none = runner.withPropertyValues(signing(key), verification(key), DEFAULT_DB_PASSWORD);

        // Act
        staging.run(stagingContext -> {
            none.run(noneContext -> {
                // Assert
                assertThat(stagingContext).hasFailed().getFailure().rootCause().hasMessageContaining("DB_PASSWORD");
                assertThat(noneContext).hasFailed().getFailure().rootCause().hasMessageContaining("DB_PASSWORD");
            });
        });
    }

    @Test
    void theDevelopmentDatabaseDefaultIsFineUnderDevAndTest() {
        // Arrange
        ApplicationContextRunner dev = withProfiles("dev", DEFAULT_DB_PASSWORD);
        ApplicationContextRunner test = withProfiles("test", DEFAULT_DB_PASSWORD);

        // Act
        dev.run(devContext -> {
            test.run(testContext -> {
                // Assert
                assertThat(devContext).hasNotFailed();
                assertThat(testContext).hasNotFailed();
            });
        });
    }

    @Test
    void aRotationOverlapStartsWithBothVerificationKeys() {
        // Arrange
        ECKey old = TestKeys.generate("old");
        ECKey next = TestKeys.generate("new");
        ApplicationContextRunner prod = withProfiles("prod", signing(next), verification(old, next), GOOD_DB_PASSWORD);

        // Act
        prod.run(context -> {
            // Assert
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtKeySet.class).verificationKeys()).hasSize(2);
        });
    }

    @Test
    void theKeyPropertiesNeverPrintTheirSecrets() {
        // Arrange
        JwtKeyProperties properties = new JwtKeyProperties("s3cr3t-signing", "s3cr3t-verification");

        // Act
        String text = properties.toString();

        // Assert
        assertThat(text).doesNotContain("s3cr3t");
    }
}
