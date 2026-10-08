package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.jwk.ECKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The access-token verifier of threat model D-1..D-4: ES256 only, closed key set by kid, iss/aud/exp/nbf with 30 s of skew. */
class AccessTokenDecoderTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final TokenForge forge = new TokenForge(NOW);
    private ECKey active;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        active = TestKeys.generate("k1");
        decoder = decoderFor(active);
    }

    private static JwtDecoder decoderFor(ECKey... keys) {
        JwtKeySet set = JwtKeyLoader.load(TestKeys.privateJwk(keys[keys.length - 1]), TestKeys.verificationSet(keys), false);
        return AccessTokenDecoderFactory.create(set, CLOCK);
    }

    private JwtException decodeFailure(String token) {
        Executable act = () -> decoder.decode(token);
        return assertThrows(JwtException.class, act);
    }

    @Test
    void acceptsATokenIssuedByTheAccessTokenIssuer() {
        // Arrange
        JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(active), TestKeys.verificationSet(active), false);
        AccessTokenIssuer issuer = new AccessTokenIssuer(keys, CLOCK);
        UUID userId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        AccessToken token = issuer.issue(userId, associationId, memberId, "stamp-1");

        // Act
        Jwt jwt = decoder.decode(token.value());

        // Assert
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("aid")).isEqualTo(associationId.toString());
        assertThat(jwt.getClaimAsString("mid")).isEqualTo(memberId.toString());
        assertThat(jwt.getClaimAsString("sv")).isEqualTo("stamp-1");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("regi-volley-api");
        assertThat(jwt.getAudience()).containsExactly("regi-volley-web");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(jwt.getHeaders()).containsEntry("kid", "k1").containsEntry("alg", "ES256");
    }

    @Test
    void theIssuedClaimsHoldIdsOnlyNoRolesAndNoPersonalData() {
        // Arrange
        JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(active), TestKeys.verificationSet(active), false);
        String token = new AccessTokenIssuer(keys, CLOCK).issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "s").value();

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getClaims().keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "aid", "mid", "sv", "iat", "exp");
    }

    @Test
    void rejectsAnExpiredToken() {
        // Arrange
        String token = forge.es256(active, c -> c.issueTime(Date.from(NOW.minusSeconds(600))).expirationTime(Date.from(NOW.minusSeconds(31))));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void toleratesThirtySecondsOfClockSkewOnExpiry() {
        // Arrange
        String token = forge.es256(active, c -> c.issueTime(Date.from(NOW.minusSeconds(600))).expirationTime(Date.from(NOW.minusSeconds(29))));

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.minusSeconds(29));
    }

    @Test
    void rejectsATokenNotValidYetBeyondTheSkew() {
        // Arrange
        String token = forge.es256(active, c -> c.notBeforeTime(Date.from(NOW.plusSeconds(31))));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void toleratesThirtySecondsOfClockSkewOnNotBefore() {
        // Arrange
        String token = forge.es256(active, c -> c.notBeforeTime(Date.from(NOW.plusSeconds(29))));

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getNotBefore()).isEqualTo(NOW.plusSeconds(29));
    }

    @Test
    void rejectsATokenIssuedInTheFutureBeyondTheSkew() {
        // Arrange
        String token = forge.es256(active, c -> c.issueTime(Date.from(NOW.plusSeconds(31))).expirationTime(Date.from(NOW.plusSeconds(631))));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void toleratesATokenIssuedThirtySecondsInTheFuture() {
        // Arrange
        String token = forge.es256(active, c -> c.issueTime(Date.from(NOW.plusSeconds(29))).expirationTime(Date.from(NOW.plusSeconds(629))));

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW.plusSeconds(29));
    }

    @Test
    void rejectsATokenThatLivesLongerThanThePolicyAllows() {
        // Arrange - a day-long token, however genuine its signature: we never mint one
        String token = forge.es256(active, c -> c.expirationTime(Date.from(NOW.plusSeconds(86_400))));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void acceptsALifetimeOfTheTtlPlusTheSkew() {
        // Arrange
        String token = forge.es256(active, c -> c.expirationTime(Date.from(NOW.plusSeconds(630))));

        // Act
        Jwt jwt = decoder.decode(token);

        // Assert
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(630));
    }

    @Test
    void rejectsALifetimeBeyondTheTtlPlusTheSkew() {
        // Arrange
        String token = forge.es256(active, c -> c.expirationTime(Date.from(NOW.plusSeconds(631))));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        // Arrange
        String token = forge.es256(active, c -> c.expirationTime(null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATamperedPayload() {
        // Arrange
        String genuine = forge.es256(active, forge.validClaims().build());
        String other = forge.es256(active, forge.validClaims().build());
        String tampered = TokenForge.withPayloadOf(genuine, other);

        // Act
        JwtException failure = decodeFailure(tampered);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsAnUnsignedAlgNoneToken() {
        // Arrange
        String token = forge.none(forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsHs256MacdWithThePublicKeyAsTheSecret() {
        // Arrange
        String token = forge.hs256WithPublicKeyAsSecret(active, forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsRs256EvenWithOurKid() {
        // Arrange
        String token = forge.rs256("k1", forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsAnEs256TokenSignedByAnotherKeyUnderOurKid() {
        // Arrange
        ECKey impostor = TestKeys.generate("k1");
        String token = forge.es256(impostor, forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsAnUnknownKid() {
        // Arrange
        ECKey stranger = TestKeys.generate("unknown");
        String token = forge.es256(stranger, forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutKidEvenWhenItsSignatureIsGenuine() {
        // Arrange
        String token = forge.es256(active, null, forge.validClaims().build());

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsAWrongAudience() {
        // Arrange
        String token = forge.es256(active, c -> c.audience("somebody-else"));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAMissingAudience() {
        // Arrange
        String token = forge.es256(active, c -> c.audience((String) null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAWrongIssuer() {
        // Arrange
        String token = forge.es256(active, c -> c.issuer("https://evil.example"));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsAMissingIssuer() {
        // Arrange
        String token = forge.es256(active, c -> c.issuer(null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsATokenWithoutSubject() {
        // Arrange
        String token = forge.es256(active, c -> c.subject(null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutAssociation() {
        // Arrange
        String token = forge.es256(active, c -> c.claim("aid", null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutMember() {
        // Arrange
        String token = forge.es256(active, c -> c.claim("mid", null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutSecurityStamp() {
        // Arrange
        String token = forge.es256(active, c -> c.claim("sv", null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsATokenWithoutIssueTime() {
        // Arrange
        String token = forge.es256(active, c -> c.issueTime(null));

        // Act
        JwtException failure = decodeFailure(token);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
    }

    @Test
    void rejectsGarbage() {
        // Arrange
        String garbage = "not-a-jwt";
        String empty = "";
        String dotted = "a.b.c";

        // Act
        JwtException first = decodeFailure(garbage);
        JwtException second = decodeFailure(empty);
        JwtException third = decodeFailure(dotted);

        // Assert
        assertThat(first).isInstanceOf(BadJwtException.class);
        assertThat(second).isInstanceOf(JwtException.class);
        assertThat(third).isInstanceOf(BadJwtException.class);
    }

    @Test
    void aTokenSignedByTheOldKeyVerifiesWhileBothPublicKeysAreConfigured() {
        // Arrange - rotation by overlap (D-2): the new key signs, the old public key stays for one access lifetime
        ECKey old = TestKeys.generate("old");
        ECKey next = TestKeys.generate("new");
        String signedByOld = forge.es256(old, forge.validClaims().build());
        String signedByNew = forge.es256(next, forge.validClaims().build());
        JwtDecoder overlap = decoderFor(old, next);

        // Act
        Jwt oldDuringOverlap = overlap.decode(signedByOld);
        Jwt newDuringOverlap = overlap.decode(signedByNew);

        // Assert
        assertThat(oldDuringOverlap.getHeaders()).containsEntry("kid", "old");
        assertThat(newDuringOverlap.getHeaders()).containsEntry("kid", "new");
    }

    @Test
    void aTokenSignedByTheOldKeyFailsOnceItsPublicKeyIsRemoved() {
        // Arrange
        ECKey old = TestKeys.generate("old");
        ECKey next = TestKeys.generate("new");
        String signedByOld = forge.es256(old, forge.validClaims().build());
        String signedByNew = forge.es256(next, forge.validClaims().build());
        JwtDecoder afterRemoval = decoderFor(next);
        Executable oldToken = () -> afterRemoval.decode(signedByOld);

        // Act
        JwtException failure = assertThrows(JwtException.class, oldToken);
        Jwt stillValid = afterRemoval.decode(signedByNew);

        // Assert
        assertThat(failure).isInstanceOf(BadJwtException.class);
        assertThat(stillValid.getHeaders()).containsEntry("kid", "new");
    }

    @Test
    void theIssuerSignsWithTheKeyOfTheSigningKidDuringARotation() {
        // Arrange
        ECKey old = TestKeys.generate("old");
        ECKey next = TestKeys.generate("new");
        JwtKeySet keys = JwtKeyLoader.load(TestKeys.privateJwk(next), TestKeys.verificationSet(old, next), false);
        AccessToken token = new AccessTokenIssuer(keys, CLOCK).issue(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "s");

        // Act
        Jwt jwt = AccessTokenDecoderFactory.create(keys, CLOCK).decode(token.value());

        // Assert
        assertThat(jwt.getHeaders()).containsEntry("kid", "new");
    }
}
