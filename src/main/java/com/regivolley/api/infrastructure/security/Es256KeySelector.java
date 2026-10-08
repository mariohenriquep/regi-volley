package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;

import java.security.Key;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The only door to signature verification (threat model D-1, D-2): a token is checked against a key only if it
 * says {@code alg: ES256} and names a {@code kid} of the closed set. Anything else (other algorithms, no kid, an unknown
 * kid) gets no candidate key, so the processor rejects it before any cryptography runs. No symmetric algorithm is
 * ever registered, so a public key can never be used as an HMAC secret.
 */
final class Es256KeySelector implements JWSKeySelector<SecurityContext> {

    private final Map<String, PublicKey> keysByKid;

    Es256KeySelector(List<ECKey> verificationKeys) {
        Map<String, PublicKey> byKid = new HashMap<>();
        for (ECKey key : verificationKeys) {
            try {
                byKid.put(key.getKeyID(), key.toECPublicKey());
            } catch (JOSEException e) {
                throw new InvalidJwtKeyConfigurationException("A verification key could not be loaded");
            }
        }
        this.keysByKid = Map.copyOf(byKid);
    }

    @Override
    public List<? extends Key> selectJWSKeys(JWSHeader header, SecurityContext context) {
        if (!JWSAlgorithm.ES256.equals(header.getAlgorithm()) || header.getKeyID() == null) {
            return List.of();
        }
        PublicKey key = keysByKid.get(header.getKeyID());
        return key == null ? List.of() : List.of(key);
    }
}
