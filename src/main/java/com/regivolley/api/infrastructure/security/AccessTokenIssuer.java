package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Mints access tokens (threat model D-1, D-4): ES256 with the active signing key, its {@code kid} in the header, and the
 * claims of {@link AccessTokenPolicy}. The login and refresh use cases of 26b call {@link #issue}; nothing else signs.
 * Time comes from the injected {@link Clock}.
 */
public class AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final String keyId;
    private final Clock clock;

    public AccessTokenIssuer(JwtKeySet keys, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(keys.signingKey())));
        this.keyId = keys.signingKey().getKeyID();
        this.clock = clock;
    }

    /**
     * @param userId         the account ({@code sub})
     * @param associationId  the selected membership's association ({@code aid})
     * @param memberId       the selected membership's member ({@code mid})
     * @param securityStamp  the account's current security stamp ({@code sv})
     */
    public AccessToken issue(UUID userId, AssociationId associationId, MemberId memberId, String securityStamp) {
        return issue(userId, associationId.value(), memberId.value(), securityStamp);
    }

    AccessToken issue(UUID userId, UUID associationId, UUID memberId, String securityStamp) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(AccessTokenPolicy.TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(AccessTokenPolicy.ISSUER)
                .audience(List.of(AccessTokenPolicy.AUDIENCE))
                .subject(userId.toString())
                .claim(AccessTokenPolicy.CLAIM_ASSOCIATION, associationId.toString())
                .claim(AccessTokenPolicy.CLAIM_MEMBER, memberId.toString())
                .claim(AccessTokenPolicy.CLAIM_SECURITY_STAMP, securityStamp)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(keyId).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
