package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds the access-token verifier (threat model D-1, D-4): Spring's {@link NimbusJwtDecoder} over a Nimbus processor
 * whose key selector is {@link Es256KeySelector} (Spring's {@code withPublicKey} shortcut is RSA-only). Nimbus' stock claims
 * verifier (60 s skew, system clock) is replaced by a presence check of every claim the principal needs; the Spring
 * validators do the rest, with the injected clock and 30 s of skew: issuer, audience, expiry and not-before. A missing
 * claim is a rejected token.
 */
final class AccessTokenDecoderFactory {

    private static final List<String> REQUIRED_CLAIMS = List.of(JwtClaimNames.SUB, JwtClaimNames.EXP, JwtClaimNames.IAT,
            AccessTokenPolicy.CLAIM_ASSOCIATION, AccessTokenPolicy.CLAIM_MEMBER, AccessTokenPolicy.CLAIM_SECURITY_STAMP);

    private AccessTokenDecoderFactory() {
    }

    static JwtDecoder create(JwtKeySet keys, Clock clock) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new Es256KeySelector(keys.verificationKeys()));
        processor.setJWTClaimsSetVerifier(AccessTokenDecoderFactory::requireClaims);
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator(clock));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> validator(Clock clock) {
        JwtTimestampValidator timestamps = new JwtTimestampValidator(AccessTokenPolicy.CLOCK_SKEW);
        timestamps.setClock(Objects.requireNonNull(clock, "clock must not be null"));
        return new DelegatingOAuth2TokenValidator<>(
                timestamps,
                lifetime(clock),
                new JwtIssuerValidator(AccessTokenPolicy.ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(AccessTokenPolicy.AUDIENCE)));
    }

    /**
     * A token that claims to have been issued in the future, or to live longer than the policy's TTL, was not minted by us
     * (or by a clock we trust), whatever its signature says: both are refused, within the policy's skew.
     */
    private static OAuth2TokenValidator<Jwt> lifetime(Clock clock) {
        OAuth2Error invalid = new OAuth2Error("invalid_token", "The token lifetime is not acceptable", null);
        return jwt -> {
            Instant issuedAt = jwt.getIssuedAt();
            Instant expiresAt = jwt.getExpiresAt();
            boolean fromTheFuture = issuedAt.isAfter(clock.instant().plus(AccessTokenPolicy.CLOCK_SKEW));
            boolean livesTooLong = Duration.between(issuedAt, expiresAt).compareTo(AccessTokenPolicy.TTL.plus(AccessTokenPolicy.CLOCK_SKEW)) > 0;
            return fromTheFuture || livesTooLong ? OAuth2TokenValidatorResult.failure(invalid) : OAuth2TokenValidatorResult.success();
        };
    }

    /**
     * Runs on the raw claims, before Spring fills in defaults (it invents a missing {@code iat}): every claim the
     * principal needs must be present.
     */
    private static void requireClaims(JWTClaimsSet claims, SecurityContext context) throws BadJWTException {
        for (String name : REQUIRED_CLAIMS) {
            if (claims.getClaim(name) == null) {
                throw new BadJWTException("A required claim is missing");
            }
        }
    }
}
