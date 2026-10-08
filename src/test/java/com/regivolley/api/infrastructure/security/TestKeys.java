package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/** Key material for the security tests, in the formats the environment variables accept. Nothing here is a real secret. */
final class TestKeys {

    private TestKeys() {
    }

    static ECKey generate(String kid) {
        return generate(kid, Curve.P_256);
    }

    static ECKey generate(String kid, Curve curve) {
        try {
            return new ECKeyGenerator(curve).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A JWK Set of the public halves, the shape of {@code JWT_VERIFICATION_KEYS}. */
    static String verificationSet(ECKey... keys) {
        List<JWK> publics = Arrays.stream(keys).map(ECKey::toPublicJWK).<JWK>map(key -> key).toList();
        return new JWKSet(publics).toString();
    }

    /** The private JWK (with {@code d}), the JWK form of {@code JWT_SIGNING_KEY}. */
    static String privateJwk(ECKey key) {
        return key.toJSONString();
    }

    /** PKCS#8 PEM of the private key, preceded by the {@code kid:} line. */
    static String privatePem(ECKey key, String kidLine) {
        try {
            return (kidLine == null ? "" : "kid: " + kidLine + "\n")
                    + pem("PRIVATE KEY", key.toECPrivateKey().getEncoded());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** X.509 PEM of the public key, preceded by the {@code kid:} line. */
    static String publicPem(ECKey key, String kidLine) {
        try {
            return (kidLine == null ? "" : "kid: " + kidLine + "\n")
                    + pem("PUBLIC KEY", key.toECPublicKey().getEncoded());
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
