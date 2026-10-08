package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.ServiceBusyException;
import com.regivolley.api.application.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.Normalizer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Password hashing (threat model D-8): a {@link DelegatingPasswordEncoder} whose default is Argon2id with the OWASP minimum
 * profile (19 MiB, 2 passes, 1 lane, 16-byte salt, 32-byte hash), accepting {@code bcrypt} (cost 12) as a legacy format that
 * {@link #needsUpgrade} flags so the login can re-hash on success. Passwords are NFKC-normalised before hashing and
 * verifying. At most {@value #MAX_CONCURRENT} hash operations run at once: each takes 19 MiB and a core, so an unbounded
 * number would let a burst of logins exhaust the container (L7). A caller waits for a slot at most {@code waitLimit} (default 2 s);
 * past that it gets {@link ServiceBusyException} (503 with {@code Retry-After}) instead of tying up a request thread for as long as the
 * queue is long. The rate limiters sit in front of this; the cap is the backstop.
 *
 * <p>{@link #burn} verifies against a precomputed hash and is what the login does for an account that does not exist, so
 * unknown and known emails cost about the same time (D-10).
 */
public class Argon2PasswordHasher implements PasswordHasher {

    static final int MAX_CONCURRENT = 4;
    static final Duration DEFAULT_WAIT_LIMIT = Duration.ofSeconds(2);
    static final String DEFAULT_ID = "argon2id";

    private static final long RETRY_AFTER_SECONDS = 2;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19 * 1024;
    private static final int ITERATIONS = 2;
    private static final int BCRYPT_COST = 12;

    private final PasswordEncoder encoder;
    private final Semaphore permits;
    private final Duration waitLimit;
    private final String dummyHash;

    /** The production encoder with the hash cap and a dummy hash precomputed at start-up. */
    public static Argon2PasswordHasher production() {
        Map<String, PasswordEncoder> encoders = Map.of(
                DEFAULT_ID, new Argon2PasswordEncoder(SALT_BYTES, HASH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS),
                "bcrypt", new BCryptPasswordEncoder(BCRYPT_COST));
        return new Argon2PasswordHasher(new DelegatingPasswordEncoder(DEFAULT_ID, encoders), MAX_CONCURRENT, DEFAULT_WAIT_LIMIT);
    }

    Argon2PasswordHasher(PasswordEncoder encoder, int maxConcurrent, Duration waitLimit) {
        this.encoder = encoder;
        this.permits = new Semaphore(maxConcurrent, true);
        this.waitLimit = waitLimit;
        this.dummyHash = encoder.encode(SecureTokens.newSecret());
    }

    @Override
    public String hash(String password) {
        return limited(() -> encoder.encode(normalise(password)));
    }

    @Override
    public boolean matches(String password, String storedHash) {
        return limited(() -> encoder.matches(normalise(password), storedHash));
    }

    @Override
    public boolean needsUpgrade(String storedHash) {
        return !(encoder instanceof DelegatingPasswordEncoder delegating) || delegating.upgradeEncoding(storedHash);
    }

    @Override
    public void burn(String password) {
        matches(password, dummyHash);
    }

    int availablePermits() {
        return permits.availablePermits();
    }

    private <T> T limited(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(waitLimit.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException(RETRY_AFTER_SECONDS);
        }
        if (!acquired) {
            throw new ServiceBusyException(RETRY_AFTER_SECONDS);
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }

    private static String normalise(String password) {
        return Normalizer.normalize(password, Normalizer.Form.NFKC);
    }
}
