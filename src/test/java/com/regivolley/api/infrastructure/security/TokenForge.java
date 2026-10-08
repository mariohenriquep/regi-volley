package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Builds tokens, valid and broken, for the security tests. Claims default to a valid access token issued at {@code now}. */
final class TokenForge {

    static final String STAMP = "stamp-1";

    private final Instant now;

    TokenForge(Instant now) {
        this.now = now;
    }

    /** The claims of a valid token for the given identity. */
    JWTClaimsSet.Builder validClaims(UUID userId, UUID associationId, UUID memberId) {
        return new JWTClaimsSet.Builder()
                .issuer(AccessTokenPolicy.ISSUER)
                .audience(AccessTokenPolicy.AUDIENCE)
                .subject(userId.toString())
                .claim(AccessTokenPolicy.CLAIM_ASSOCIATION, associationId.toString())
                .claim(AccessTokenPolicy.CLAIM_MEMBER, memberId.toString())
                .claim(AccessTokenPolicy.CLAIM_SECURITY_STAMP, STAMP)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
    }

    JWTClaimsSet.Builder validClaims() {
        return validClaims(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    String es256(ECKey key, JWTClaimsSet claims) {
        return es256(key, key.getKeyID(), claims);
    }

    String es256(ECKey key, String kidInHeader, JWTClaimsSet claims) {
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.ES256);
        if (kidInHeader != null) {
            header.keyID(kidInHeader);
        }
        try {
            return sign(new SignedJWT(header.build(), claims), new ECDSASigner(key));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    String es256(ECKey key, Consumer<JWTClaimsSet.Builder> customise) {
        JWTClaimsSet.Builder claims = validClaims();
        customise.accept(claims);
        return es256(key, claims.build());
    }

    /** {@code alg: none}: an unsecured token with valid-looking claims. */
    String none(JWTClaimsSet claims) {
        return new PlainJWT(claims).serialize();
    }

    /** {@code alg: HS256}, MAC'd with the bytes of the public key as the secret: the classic algorithm-confusion attempt. */
    String hs256WithPublicKeyAsSecret(ECKey publicKey, JWTClaimsSet claims) {
        try {
            byte[] secret = publicKey.toECPublicKey().getEncoded();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(publicKey.getKeyID()).build();
            return sign(new SignedJWT(header, claims), new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code alg: RS256} signed with an unrelated RSA key, but carrying our kid. */
    String rs256(String kid, JWTClaimsSet claims) {
        try {
            RSAKey rsa = new RSAKeyGenerator(2048).keyID(kid).generate();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build();
            return sign(new SignedJWT(header, claims), new RSASSASigner(rsa));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Replaces the payload of a signed token and keeps its signature: a tampered token. */
    static String withPayloadOf(String token, String otherToken) {
        String[] parts = token.split("\\.");
        String[] other = otherToken.split("\\.");
        return parts[0] + "." + other[1] + "." + parts[2];
    }

    static List<String> audience(String... values) {
        return List.of(values);
    }

    private static String sign(SignedJWT jwt, JWSSigner signer) throws JOSEException {
        jwt.sign(signer);
        return jwt.serialize();
    }
}
