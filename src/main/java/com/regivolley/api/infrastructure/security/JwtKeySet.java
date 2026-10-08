package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.jwk.ECKey;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The validated keys of the access-token signature scheme (threat model D-1, D-2): the one private key that signs and
 * the closed set of public keys that verify, always including the signing key's own public half. During a rotation
 * the set holds two keys. Built only by {@link JwtKeyLoader}, which has checked curve, kid, pairing and duplicates.
 *
 * <p>{@link #toString()} lists key ids only: key material must never reach a log.
 */
public record JwtKeySet(ECKey signingKey, List<ECKey> verificationKeys, boolean ephemeral) {

    public JwtKeySet {
        Objects.requireNonNull(signingKey, "signingKey must not be null");
        verificationKeys = List.copyOf(verificationKeys);
    }

    @Override
    public String toString() {
        return "JwtKeySet{signingKid=%s, verificationKids=%s, ephemeral=%s}".formatted(
                signingKey.getKeyID(),
                verificationKeys.stream().map(ECKey::getKeyID).collect(Collectors.toList()),
                ephemeral);
    }
}
