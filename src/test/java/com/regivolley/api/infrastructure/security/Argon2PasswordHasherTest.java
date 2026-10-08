package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import com.regivolley.api.application.exception.ServiceBusyException;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-8: Argon2id by default, bcrypt accepted and upgraded, never more than four hashes at once. */
class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = Argon2PasswordHasher.production();

    @Test
    void hashesWithArgon2idAndVerifies() {
        // Arrange
        String password = "correct horse battery staple";

        // Act
        String hash = hasher.hash(password);

        // Assert
        assertThat(hash).startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasher.matches(password, hash)).isTrue();
        assertThat(hasher.matches("another password!", hash)).isFalse();
        assertThat(hasher.needsUpgrade(hash)).isFalse();
    }

    @Test
    void twoHashesOfTheSamePasswordDiffer() {
        // Arrange
        String password = "correct horse battery staple";

        // Act
        String first = hasher.hash(password);
        String second = hasher.hash(password);

        // Assert
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void acceptsALegacyBcryptHashAndAsksForItsUpgrade() {
        // Arrange
        String legacy = "{bcrypt}" + new BCryptPasswordEncoder(12).encode("correct horse battery staple");

        // Act
        boolean matches = hasher.matches("correct horse battery staple", legacy);

        // Assert
        assertThat(matches).isTrue();
        assertThat(hasher.matches("wrong password here", legacy)).isFalse();
        assertThat(hasher.needsUpgrade(legacy)).isTrue();
    }

    @Test
    void passwordsAreNormalisedBeforeHashingSoEquivalentFormsMatch() {
        // Arrange
        String composed = "café au lait 42";
        String decomposed = "café au lait 42";

        // Act
        String hash = hasher.hash(composed);

        // Assert
        assertThat(hasher.matches(decomposed, hash)).isTrue();
    }

    @Test
    void burningAHashTakesTheSamePathAsAVerification() {
        // Arrange
        AtomicInteger verifications = new AtomicInteger();
        PasswordEncoder counting = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{noop}dummy";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                verifications.incrementAndGet();
                return false;
            }
        };
        Argon2PasswordHasher counted = new Argon2PasswordHasher(counting, 4, Duration.ofSeconds(30));

        // Act
        counted.burn("whatever the client sent");

        // Assert
        assertThat(verifications).hasValue(1);
    }

    @Test
    void neverRunsMoreThanTheCappedNumberOfHashesAtOnce() throws Exception {
        // Arrange
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        PasswordEncoder slow = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{noop}x";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                int now = running.incrementAndGet();
                peak.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                running.decrementAndGet();
                return true;
            }
        };
        Argon2PasswordHasher capped = new Argon2PasswordHasher(slow, 4, Duration.ofSeconds(30));
        int callers = 16;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);

        // Act
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return capped.matches("password-here", "{noop}x");
            }));
        }
        go.countDown();
        for (Future<Boolean> result : results) {
            result.get();
        }
        pool.shutdown();

        // Assert
        assertThat(peak.get()).isLessThanOrEqualTo(4).isGreaterThan(1);
    }

    @Test
    void aFailingEncoderReleasesItsPermit() {
        // Arrange
        AtomicInteger encodes = new AtomicInteger();
        PasswordEncoder broken = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                if (encodes.incrementAndGet() == 1) {
                    return "{noop}dummy"; // the dummy hash computed when the hasher is built
                }
                throw new IllegalStateException("boom");
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                throw new IllegalStateException("boom");
            }
        };
        Argon2PasswordHasher single = new Argon2PasswordHasher(broken, 1, Duration.ofSeconds(30));

        // Act
        for (int i = 0; i < 3; i++) {
            try {
                single.hash("password-here");
            } catch (IllegalStateException expected) {
                // the permit must come back, or the third call would block forever
            }
        }

        // Assert
        assertThat(single.availablePermits()).isEqualTo(1);
    }

    @Test
    void whenEverySlotStaysBusyPastTheWaitTheCallerGetsServiceBusyNotAnEndlessQueue() throws Exception {
        // Arrange - one slot, held by a slow verification in another thread
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        PasswordEncoder blocking = new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{noop}x";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                holding.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return true;
            }
        };
        Argon2PasswordHasher busy = new Argon2PasswordHasher(blocking, 1, Duration.ofMillis(100));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Boolean> holder = pool.submit(() -> busy.matches("first-password", "{noop}x"));
        holding.await();
        Executable act = () -> busy.matches("second-password", "{noop}x");

        // Act
        ServiceBusyException ex = assertThrows(ServiceBusyException.class, act);
        release.countDown();
        holder.get();
        pool.shutdown();

        // Assert
        assertThat(ex.retryAfterSeconds()).isPositive();
        assertThat(busy.availablePermits()).isEqualTo(1);
    }

    @Test
    void anInterruptedWaitIsAlsoServiceBusyAndKeepsTheInterruptFlag() {
        // Arrange
        Argon2PasswordHasher single = new Argon2PasswordHasher(new PasswordEncoder() {
            @Override
            public String encode(CharSequence raw) {
                return "{noop}x";
            }

            @Override
            public boolean matches(CharSequence raw, String encoded) {
                return true;
            }
        }, 1, Duration.ofSeconds(5));
        single.availablePermits();
        Thread.currentThread().interrupt();
        Executable act = () -> single.hash("some password here");

        // Act
        ServiceBusyException ex = assertThrows(ServiceBusyException.class, act);
        boolean interrupted = Thread.interrupted();

        // Assert
        assertThat(ex).isNotNull();
        assertThat(interrupted).isTrue();
    }
}
