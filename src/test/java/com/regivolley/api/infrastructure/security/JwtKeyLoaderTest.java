package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtKeyLoaderTest {

    private static InvalidJwtKeyConfigurationException rejection(String signing, String verification, boolean ephemeralAllowed) {
        Executable act = () -> JwtKeyLoader.load(signing, verification, ephemeralAllowed);
        return assertThrows(InvalidJwtKeyConfigurationException.class, act);
    }

    @Nested
    class ConfiguredKeys {

        @Test
        void loadsAPrivateJwkAndAJwkSetOfPublicKeys() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(active), TestKeys.verificationSet(active), false);

            // Assert
            assertThat(keys.signingKey().getKeyID()).isEqualTo("k1");
            assertThat(keys.signingKey().isPrivate()).isTrue();
            assertThat(keys.verificationKeys()).extracting(ECKey::getKeyID).containsExactly("k1");
            assertThat(keys.verificationKeys()).allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
        }

        @Test
        void loadsPemKeysWhoseKidIsGivenOnTheLineBeforeEachBlock() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            String signing = TestKeys.privatePem(active, "k1");
            String verification = TestKeys.publicPem(active, "k1");

            // Act
            JwtKeySet keys = JwtKeyLoader.load(signing, verification, false);

            // Assert
            assertThat(keys.signingKey().getKeyID()).isEqualTo("k1");
            assertThat(keys.signingKey().isPrivate()).isTrue();
            assertThat(keys.verificationKeys()).extracting(ECKey::getKeyID).containsExactly("k1");
        }

        @Test
        void keepsBothPublicKeysDuringARotationOverlap() {
            // Arrange
            ECKey old = TestKeys.generate("old");
            ECKey next = TestKeys.generate("new");

            // Act
            JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(next), TestKeys.verificationSet(old, next), false);

            // Assert
            assertThat(keys.signingKey().getKeyID()).isEqualTo("new");
            assertThat(keys.verificationKeys()).extracting(ECKey::getKeyID).containsExactlyInAnyOrder("old", "new");
        }

        @Test
        void keyMaterialNeverAppearsInToString() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(active), TestKeys.verificationSet(active), false);

            // Act
            String text = keys.toString();

            // Assert
            assertThat(text).contains("k1").doesNotContain(active.getD().toString()).doesNotContain(active.getX().toString());
        }
    }

    @Nested
    class Rejected {

        @Test
        void aMissingSigningKeyWhenNoEphemeralKeyIsAllowed() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException blank = rejection("", TestKeys.verificationSet(active), false);
            InvalidJwtKeyConfigurationException missing = rejection(null, "", false);

            // Assert
            assertThat(blank.getMessage()).contains("JWT_SIGNING_KEY");
            assertThat(missing.getMessage()).contains("JWT_SIGNING_KEY");
        }

        @Test
        void aSigningKeyOnAnotherCurve() {
            // Arrange
            ECKey p384 = TestKeys.generate("k1", Curve.P_384);

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(p384), TestKeys.verificationSet(p384), false);

            // Assert
            assertThat(e.getMessage()).contains("P-256");
        }

        @Test
        void aVerificationKeyOnAnotherCurve() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey p384 = TestKeys.generate("k2", Curve.P_384);

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.verificationSet(active, p384), false);

            // Assert
            assertThat(e.getMessage()).contains("P-256");
        }

        @Test
        void aSigningKeyWithoutKid() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            String pemWithoutKid = TestKeys.privatePem(active, null);

            // Act
            InvalidJwtKeyConfigurationException e = rejection(pemWithoutKid, TestKeys.publicPem(active, "k1"), false);

            // Assert
            assertThat(e.getMessage()).contains("kid");
        }

        @Test
        void aVerificationKeyWithoutKid() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.publicPem(active, null), false);

            // Assert
            assertThat(e.getMessage()).contains("kid");
        }

        @Test
        void aSigningKeyWhoseKidIsNotInTheVerificationSet() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey other = TestKeys.generate("k2");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.verificationSet(other), false);

            // Assert
            assertThat(e.getMessage()).contains("JWT_VERIFICATION_KEYS");
        }

        @Test
        void aJwkSigningKeyWhoseVerificationKeyHasTheSameKidButOtherMaterial() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey impostor = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.verificationSet(impostor), false);

            // Assert
            assertThat(e.getMessage()).contains("does not match");
        }

        @Test
        void aPemSigningKeyWhoseVerificationKeyHasTheSameKidButOtherMaterial() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey impostor = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(
                    TestKeys.privatePem(active, "k1"), TestKeys.publicPem(impostor, "k1"), false);

            // Assert
            assertThat(e.getMessage()).contains("does not match");
        }

        @Test
        void duplicateKidsInTheVerificationSet() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey second = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.verificationSet(active, second), false);

            // Assert
            assertThat(e.getMessage()).contains("duplicate");
        }

        @Test
        void theSameKeyUnderTwoKids() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey alias = new ECKey.Builder(active).keyID("k2").build();

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.verificationSet(active, alias), false);

            // Assert
            assertThat(e.getMessage()).contains("duplicate");
        }

        @Test
        void aPrivateJwkInTheVerificationSet() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            String withPrivate = "{\"keys\":[" + TestKeys.privateJwk(active) + "]}";

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), withPrivate, false);

            // Assert
            assertThat(e.getMessage()).contains("private");
        }

        @Test
        void aPrivatePemInTheVerificationSet() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), TestKeys.privatePem(active, "k1"), false);

            // Assert
            assertThat(e.getMessage()).contains("private");
        }

        @Test
        void aJwkSigningKeyWithoutAVerificationSetOutsideDevelopment() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(active), "", false);

            // Assert
            assertThat(e.getMessage()).contains("JWT_VERIFICATION_KEYS");
        }

        @Test
        void aPemSigningKeyWithoutAMatchingPublicKey() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privatePem(active, "k1"), "", false);

            // Assert
            assertThat(e.getMessage()).contains("JWT_VERIFICATION_KEYS");
        }

        @Test
        void unparseableMaterialWithoutEchoingIt() {
            // Arrange
            String garbage = "not a key at all s3cr3t";

            // Act
            InvalidJwtKeyConfigurationException e = rejection(garbage, "", true);

            // Assert
            assertThat(e.getMessage()).doesNotContain("s3cr3t");
        }

        @Test
        void aNonEcKey() {
            // Arrange
            String symmetric = "{\"kty\":\"oct\",\"kid\":\"k1\",\"k\":\"bm90LWEtcmVhbC1rZXk\"}";

            // Act
            InvalidJwtKeyConfigurationException e = rejection(symmetric, "", true);

            // Assert
            assertThat(e.getMessage()).contains("EC");
        }

        @Test
        void aJwkMeantForAnotherAlgorithm() {
            // Arrange
            ECKey active = TestKeys.generate("k1");
            ECKey wrongAlg = new ECKey.Builder(active).algorithm(JWSAlgorithm.ES384).build();

            // Act
            InvalidJwtKeyConfigurationException e = rejection(TestKeys.privateJwk(wrongAlg), TestKeys.verificationSet(active), false);

            // Assert
            assertThat(e.getMessage()).contains("ES256");
        }
    }

    @Nested
    class Ephemeral {

        @Test
        void generatesAnEphemeralKeyWhenAllowedAndNothingIsConfigured() {
            // Arrange
            boolean allowed = true;

            // Act
            JwtKeySet keys = JwtKeyLoader.load("", "", allowed);

            // Assert
            assertThat(keys.signingKey().getKeyID()).startsWith("dev-");
            assertThat(keys.signingKey().getCurve()).isEqualTo(Curve.P_256);
            assertThat(keys.verificationKeys()).extracting(ECKey::getKeyID).containsExactly(keys.signingKey().getKeyID());
            assertThat(keys.ephemeral()).isTrue();
        }

        @Test
        void generatesADifferentKeyOnEveryStart() {
            // Arrange
            boolean allowed = true;

            // Act
            JwtKeySet first = JwtKeyLoader.load(null, null, allowed);
            JwtKeySet second = JwtKeyLoader.load(null, null, allowed);

            // Assert
            assertThat(first.signingKey().getKeyID()).isNotEqualTo(second.signingKey().getKeyID());
        }

        @Test
        void keepsConfiguredVerificationKeysNextToTheEphemeralOne() {
            // Arrange
            ECKey other = TestKeys.generate("other");

            // Act
            JwtKeySet keys = JwtKeyLoader.load("", TestKeys.verificationSet(other), true);

            // Assert
            assertThat(keys.verificationKeys()).hasSize(2).extracting(ECKey::getKeyID).contains("other");
        }

        @Test
        void aConfiguredJwkIsUsedEvenWhereTheEphemeralFallbackIsAllowed() {
            // Arrange
            ECKey active = TestKeys.generate("k1");

            // Act
            JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(active), "", true);

            // Assert - a JWK carries its public half, so the verification set can be left out in development
            assertThat(keys.signingKey().getKeyID()).isEqualTo("k1");
            assertThat(keys.verificationKeys()).extracting(ECKey::getKeyID).containsExactly("k1");
            assertThat(keys.ephemeral()).isFalse();
        }
    }
}
