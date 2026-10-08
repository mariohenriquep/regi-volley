package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The filter chain end to end (threat model section 10.2, 10.3): every bad or revoked token is the same 401. */
class TokenAuthenticationTest extends AbstractSecuredWebTest {

    private static final String PROBE = "/api/v1/me";
    private static final String CONSTANT_401 = "UNAUTHENTICATED|Authentication is required|3";
    private static final Instant NOW = SecurityFixtures.NOW;

    private final TokenForge forge = new TokenForge(NOW);
    private final JsonMapper json = JsonMapper.builder().build();

    /** Asserts the one 401 every failure shares and returns its body without the per-request id, to compare across failures. */
    private String constant401Of(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        String raw = result.andReturn().getResponse().getContentAsString();
        JsonNode body = json.readTree(raw);
        assertThat(result.andReturn().getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.get("requestId").asString()).isNotBlank();
        assertThat(raw).doesNotContain("error_description");
        return body.get("code").asString() + "|" + body.get("message").asString() + "|" + body.size();
    }

    private String tokenWith(Consumer<JWTClaimsSet.Builder> customise) {
        JWTClaimsSet.Builder claims = forge.validClaims(userId, association.id().value(), member.id().value());
        customise.accept(claims);
        return forge.es256(keys.signingKey(), claims.build());
    }

    private String brokenToken(String kind) {
        ECKey signing = keys.signingKey();
        return switch (kind) {
            case "expired" -> tokenWith(c -> c.issueTime(Date.from(NOW.minusSeconds(600))).expirationTime(Date.from(NOW.minusSeconds(60))));
            case "not-yet-valid" -> tokenWith(c -> c.notBeforeTime(Date.from(NOW.plusSeconds(120))));
            case "tampered" -> TokenForge.withPayloadOf(validToken(), tokenWith(c -> c.claim("mid", UUID.randomUUID().toString())));
            case "alg-none" -> forge.none(forge.validClaims(userId, association.id().value(), member.id().value()).build());
            case "hs256-with-public-key" -> forge.hs256WithPublicKeyAsSecret(signing.toPublicJWK(), forge.validClaims().build());
            case "rs256" -> forge.rs256(signing.getKeyID(), forge.validClaims().build());
            case "wrong-audience" -> tokenWith(c -> c.audience("somebody-else"));
            case "wrong-issuer" -> tokenWith(c -> c.issuer("somebody-else"));
            case "unknown-kid" -> forge.es256(TestKeys.generate("unknown-kid"), forge.validClaims().build());
            case "foreign-signature" -> forge.es256(TestKeys.generate(signing.getKeyID()), forge.validClaims().build());
            case "no-aid" -> tokenWith(c -> c.claim("aid", null));
            case "no-mid" -> tokenWith(c -> c.claim("mid", null));
            case "no-sv" -> tokenWith(c -> c.claim("sv", null));
            case "day-long" -> tokenWith(c -> c.expirationTime(Date.from(NOW.plusSeconds(86_400))));
            case "garbage" -> "garbage";
            default -> throw new IllegalArgumentException(kind);
        };
    }

    @Test
    void aValidTokenReachesTheProbeAndYieldsTheActor() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(member.id().toString()))
                .andExpect(jsonPath("$.associationId").value(association.id().toString()));
    }

    @Test
    void noTokenIsA401WithAConstantBodyAndNoErrorDescription() throws Exception {
        // Arrange
        String request = PROBE;

        // Act
        ResultActions result = mockMvc.perform(get(request));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"expired", "not-yet-valid", "tampered", "alg-none", "hs256-with-public-key", "rs256", "wrong-audience",
            "wrong-issuer", "unknown-kid", "foreign-signature", "no-aid", "no-mid", "no-sv", "day-long", "garbage"})
    void everyBrokenTokenGetsExactlyTheSame401(String kind) throws Exception {
        // Arrange
        String authorization = "Bearer " + brokenToken(kind);

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aTokenInTheQueryStringIsNotAccepted() throws Exception {
        // Arrange
        String token = validToken();

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).queryParam("access_token", token));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aTokenInAFormBodyIsNotAccepted() throws Exception {
        // Arrange
        String token = validToken();

        // Act
        ResultActions result = mockMvc.perform(post(PROBE).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("access_token", token));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aNonBearerSchemeIsNotAccepted() throws Exception {
        // Arrange
        String authorization = "Basic " + validToken();

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aSecurityStampThatChangedKillsTheTokenAtOnce() throws Exception {
        // Arrange
        String authorization = bearer();
        mockMvc.perform(get(PROBE).header("Authorization", authorization)).andExpect(status().isOk());
        accounts.registerActive(userId, association.id(), member.id(), "stamp-after-password-change");

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aDeactivatedMembersStillValidTokenIsRejectedOnTheNextCall() throws Exception {
        // Arrange
        String authorization = bearer();
        mockMvc.perform(get(PROBE).header("Authorization", authorization)).andExpect(status().isOk());
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member.deactivate()));

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void anAnonymisedMembersStillValidTokenIsRejectedOnTheNextCall() throws Exception {
        // Arrange
        String authorization = bearer();
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member.anonymise(SecurityFixtures.CLOCK)));

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aPendingMembershipIsRejected() throws Exception {
        // Arrange
        String authorization = bearer();
        accounts.register(userId, association.id(), member.id(), new SecurityAccount(UserStatus.ACTIVE, STAMP, MembershipStatus.PENDING));

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aDisabledAccountIsRejected() throws Exception {
        // Arrange
        String authorization = bearer();
        accounts.register(userId, association.id(), member.id(), new SecurityAccount(UserStatus.DISABLED, STAMP, MembershipStatus.CONFIRMED));

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void anUnknownAccountIsRejected() throws Exception {
        // Arrange
        String authorization = bearer();
        accounts.clear();

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aGenuineTokenForAUserWithNoMembershipAtThatMemberIsRejected() throws Exception {
        // Arrange
        String authorization = "Bearer " + issuer.issue(UUID.randomUUID(), association.id(), member.id(), STAMP).value();

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void anUnknownPathIsA401NotA404ForAnonymousCallers() throws Exception {
        // Arrange
        String unknown = "/api/v1/does-not-exist";

        // Act
        ResultActions result = mockMvc.perform(get(unknown));

        // Assert - default deny: no route enumeration
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aStaleAuthorizationHeaderDoesNotTurnAPublicRouteInto401() throws Exception {
        // Arrange - the error page is public; a leftover, expired or garbage Authorization header must not reach the decoder
        String stale = "Bearer " + brokenToken("expired");

        // Act
        ResultActions result = mockMvc.perform(get("/error").header("Authorization", stale));

        // Assert
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aStaleAuthorizationHeaderStillFailsOnAnAuthenticatedRoute() throws Exception {
        // Arrange
        String stale = "Bearer " + brokenToken("expired");

        // Act
        ResultActions result = mockMvc.perform(get(PROBE).header("Authorization", stale));

        // Assert
        assertThat(constant401Of(result)).isEqualTo(CONSTANT_401);
    }

    @Test
    void aDatabaseFailureWhileResolvingThePrincipalIsNeverAnAuthenticatedRequest() {
        // Arrange
        when(members.findById(association.id(), member.id())).thenThrow(new IllegalStateException("db down for ana.silva@example.com"));
        String authorization = bearer();
        Executable act = () -> mockMvc.perform(get(PROBE).header("Authorization", authorization));

        // Act
        Throwable failure = assertThrows(Exception.class, act);

        // Assert - the exception leaves the filter chain (the container turns it into the generic 500, see RawHttpSecurityIntegrationTest)
        assertThat(failure).isInstanceOf(IllegalStateException.class);
    }
}
