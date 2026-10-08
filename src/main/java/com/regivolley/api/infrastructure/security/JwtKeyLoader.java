package com.regivolley.api.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns {@code JWT_SIGNING_KEY} and {@code JWT_VERIFICATION_KEYS} into a validated {@link JwtKeySet}
 * (threat model D-2). Pure parsing and checking, no Spring: the profile decision (is an ephemeral key allowed?) is made by
 * the caller.
 *
 * <p>Accepted formats, for both variables:
 * <ul>
 *   <li>JWK JSON (a single key or a JWK Set); the {@code kid} is a member of the JWK;</li>
 *   <li>PEM blocks (PKCS#8 {@code PRIVATE KEY}, X.509 {@code PUBLIC KEY}), each preceded by a line {@code kid: <id>},
 *       because PEM has no place for a key id.</li>
 * </ul>
 * A PEM private key carries no public half the JDK can read, so it is paired with the verification key of the same
 * kid (which the set must contain anyway); a JWK private key brings its own.
 *
 * <p>Every failure is an {@link InvalidJwtKeyConfigurationException} that names the problem and never echoes the input.
 */
public final class JwtKeyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JwtKeyLoader.class);
    private static final Pattern KID_LINE = Pattern.compile("(?m)^\\s*kid\\s*:\\s*(\\S+)\\s*$");
    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN ([A-Z ]+)-----([A-Za-z0-9+/=\\s]+)-----END \\1-----");
    private static final String SIGNING_VAR = "JWT_SIGNING_KEY";
    private static final String VERIFICATION_VAR = "JWT_VERIFICATION_KEYS";

    private JwtKeyLoader() {
    }

    /**
     * @param signingKey         the active private key, or blank
     * @param verificationKeys   the public keys, or blank
     * @param ephemeralAllowed   when nothing is configured, generate a throwaway key pair instead of failing (dev and test only)
     */
    public static JwtKeySet load(String signingKey, String verificationKeys, boolean ephemeralAllowed) {
        List<Parsed> verification = parse(verificationKeys, VERIFICATION_VAR);
        List<ECKey> publicKeys = new ArrayList<>();
        for (Parsed parsed : verification) {
            publicKeys.add(parsed.asVerificationKey());
        }
        requireNoDuplicates(publicKeys);

        if (isBlank(signingKey)) {
            if (!ephemeralAllowed) {
                throw new InvalidJwtKeyConfigurationException(SIGNING_VAR + " is required");
            }
            return ephemeral(publicKeys);
        }

        List<Parsed> signing = parse(signingKey, SIGNING_VAR);
        if (signing.size() != 1) {
            throw new InvalidJwtKeyConfigurationException(SIGNING_VAR + " must hold exactly one private key");
        }
        Parsed active = signing.get(0);
        if (!active.isPrivate()) {
            throw new InvalidJwtKeyConfigurationException(SIGNING_VAR + " must be a private key");
        }
        if (ephemeralAllowed && publicKeys.stream().noneMatch(k -> k.getKeyID().equals(active.kid()))
                && active.jwk() != null) {
            // Development convenience: a JWK carries its public half, so the set may be left out.
            publicKeys.add(active.jwk().toPublicJWK());
        }
        ECKey matching = publicKeys.stream().filter(k -> k.getKeyID().equals(active.kid())).findFirst()
                .orElseThrow(() -> new InvalidJwtKeyConfigurationException(
                        "The signing key's public half must be in " + VERIFICATION_VAR + " under the same kid"));
        ECKey signingJwk = active.toSigningKey(matching);
        requirePair(signingJwk, matching);
        return new JwtKeySet(signingJwk, publicKeys, false);
    }

    private static JwtKeySet ephemeral(List<ECKey> configuredPublicKeys) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair pair = generator.generateKeyPair();
            String kid = "dev-" + UUID.randomUUID().toString().substring(0, 8);
            ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic())
                    .privateKey((ECPrivateKey) pair.getPrivate())
                    .keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).build();
            List<ECKey> all = new ArrayList<>(configuredPublicKeys);
            all.add(key.toPublicJWK());
            LOG.warn("No JWT signing key configured: using an ephemeral development key kid={}; tokens die on restart", kid);
            return new JwtKeySet(key, all, true);
        } catch (GeneralSecurityException e) {
            throw new InvalidJwtKeyConfigurationException("Could not generate the ephemeral development key");
        }
    }

    // ---- parsing -------------------------------------------------------------------------------------------------

    private static List<Parsed> parse(String text, String variable) {
        if (isBlank(text)) {
            return List.of();
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("{")) {
            return parseJwk(trimmed, variable);
        }
        if (trimmed.contains("-----BEGIN")) {
            return parsePem(trimmed, variable);
        }
        throw new InvalidJwtKeyConfigurationException(variable + " is neither JWK JSON nor PEM");
    }

    private static List<Parsed> parseJwk(String json, String variable) {
        List<JWK> jwks;
        try {
            jwks = json.contains("\"keys\"") ? JWKSet.parse(json).getKeys() : List.of(JWK.parse(json));
        } catch (ParseException e) {
            throw new InvalidJwtKeyConfigurationException(variable + " is not valid JWK JSON");
        }
        List<Parsed> parsed = new ArrayList<>();
        for (JWK jwk : jwks) {
            if (!(jwk instanceof ECKey ecKey)) {
                throw new InvalidJwtKeyConfigurationException(variable + " must hold EC keys only");
            }
            requireCurve(ecKey.getCurve(), variable);
            if (isBlank(ecKey.getKeyID())) {
                throw new InvalidJwtKeyConfigurationException("Every key in " + variable + " needs a kid");
            }
            if (ecKey.getAlgorithm() != null && !JWSAlgorithm.ES256.equals(ecKey.getAlgorithm())) {
                throw new InvalidJwtKeyConfigurationException("Every key in " + variable + " must be for ES256 or name no algorithm");
            }
            parsed.add(new Parsed(ecKey.getKeyID(), ecKey, null, null));
        }
        return parsed;
    }

    private static List<Parsed> parsePem(String pem, String variable) {
        List<Parsed> parsed = new ArrayList<>();
        Matcher block = PEM_BLOCK.matcher(pem);
        int previousEnd = 0;
        while (block.find()) {
            String label = block.group(1);
            String preceding = pem.substring(previousEnd, block.start());
            previousEnd = block.end();
            Matcher kidLine = KID_LINE.matcher(preceding);
            String kid = null;
            while (kidLine.find()) {
                kid = kidLine.group(1);
            }
            if (kid == null) {
                throw new InvalidJwtKeyConfigurationException(
                        "Every PEM key in " + variable + " needs a kid: put a line 'kid: <id>' before it");
            }
            byte[] der = decodeBase64(block.group(2), variable);
            try {
                KeyFactory factory = KeyFactory.getInstance("EC");
                if ("PRIVATE KEY".equals(label)) {
                    ECPrivateKey key = (ECPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(der));
                    requireCurve(Curve.forECParameterSpec(key.getParams()), variable);
                    parsed.add(new Parsed(kid, null, key, null));
                } else if ("PUBLIC KEY".equals(label)) {
                    ECPublicKey key = (ECPublicKey) factory.generatePublic(new X509EncodedKeySpec(der));
                    requireCurve(Curve.forECParameterSpec(key.getParams()), variable);
                    parsed.add(new Parsed(kid, null, null, key));
                } else {
                    throw new InvalidJwtKeyConfigurationException(
                            variable + " holds an unsupported PEM block (use PRIVATE KEY in PKCS#8 or PUBLIC KEY)");
                }
            } catch (GeneralSecurityException | ClassCastException e) {
                throw new InvalidJwtKeyConfigurationException(variable + " holds a PEM block that is not an EC key");
            }
        }
        if (parsed.isEmpty()) {
            throw new InvalidJwtKeyConfigurationException(variable + " holds no readable PEM block");
        }
        return parsed;
    }

    private static byte[] decodeBase64(String body, String variable) {
        try {
            return Base64.getMimeDecoder().decode(body.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new InvalidJwtKeyConfigurationException(variable + " holds a PEM block that is not valid Base64");
        }
    }

    // ---- validation ----------------------------------------------------------------------------------------------

    private static void requireCurve(Curve curve, String variable) {
        if (!Curve.P_256.equals(curve)) {
            throw new InvalidJwtKeyConfigurationException(variable + " must hold keys on curve P-256 only (ES256)");
        }
    }

    private static void requireNoDuplicates(List<ECKey> keys) {
        Set<String> kids = new HashSet<>();
        Set<String> points = new HashSet<>();
        for (ECKey key : keys) {
            if (!kids.add(key.getKeyID())) {
                throw new InvalidJwtKeyConfigurationException(VERIFICATION_VAR + " has a duplicate kid");
            }
            if (!points.add(key.getX() + "." + key.getY())) {
                throw new InvalidJwtKeyConfigurationException(VERIFICATION_VAR + " has a duplicate key under two kids");
            }
        }
    }

    /** The private key really belongs to the verification key of its kid: sign a random message, verify it with the public key. */
    private static void requirePair(ECKey signing, ECKey verification) {
        if (!signing.getX().equals(verification.getX()) || !signing.getY().equals(verification.getY())) {
            throw new InvalidJwtKeyConfigurationException("The signing key does not match its verification key");
        }
        try {
            byte[] message = new byte[32];
            new SecureRandom().nextBytes(message);
            JWSHeader header = new JWSHeader(JWSAlgorithm.ES256);
            var signature = new ECDSASigner(signing).sign(header, message);
            if (!new ECDSAVerifier(verification.toPublicJWK()).verify(header, message, signature)) {
                throw new InvalidJwtKeyConfigurationException("The signing key does not match its verification key");
            }
        } catch (JOSEException e) {
            throw new InvalidJwtKeyConfigurationException("The signing key does not match its verification key");
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** One key as read: a JWK, or the private or public half of a PEM with the kid from its preceding line. */
    private record Parsed(String kid, ECKey jwk, ECPrivateKey pemPrivate, ECPublicKey pemPublic) {

        boolean isPrivate() {
            return pemPrivate != null || (jwk != null && jwk.isPrivate());
        }

        ECKey asVerificationKey() {
            if (isPrivate()) {
                throw new InvalidJwtKeyConfigurationException(VERIFICATION_VAR + " must hold public keys only, not private ones");
            }
            if (jwk != null) {
                return jwk;
            }
            return new ECKey.Builder(Curve.P_256, pemPublic).keyID(kid).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256).build();
        }

        ECKey toSigningKey(ECKey publicHalf) {
            if (jwk != null) {
                return new ECKey.Builder(jwk).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).build();
            }
            try {
                return new ECKey.Builder(Curve.P_256, publicHalf.toECPublicKey()).privateKey(pemPrivate)
                        .keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.ES256).build();
            } catch (JOSEException e) {
                throw new InvalidJwtKeyConfigurationException("The signing key does not match its verification key");
            }
        }
    }
}
