package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import com.regivolley.api.application.exception.RateLimitExceededException;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-9: token buckets per rule and key, an answer that says when to retry, no hard lock. */
class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T09:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    private boolean allowed(RateLimitRule rule, String key) {
        try {
            limiter.check(rule, key);
            return true;
        } catch (RateLimitExceededException e) {
            return false;
        }
    }

    private void useUp(RateLimitRule rule, String key) {
        for (int i = 0; i < rule.capacity(); i++) {
            limiter.check(rule, key);
        }
    }

    @Test
    void allowsTheCapacityAndRefusesTheNextCallWithARetryAfter() {
        // Arrange
        RateLimitRule rule = RateLimitRule.LOGIN_EMAIL;
        useUp(rule, "key");
        Executable act = () -> limiter.check(rule, "key");

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, rule.window().toSeconds());
    }

    @ParameterizedTest
    @CsvSource({
            "LOGIN_EMAIL, 5, PT15M", "LOGIN_IP, 30, PT10M", "RESET_EMAIL, 3, PT1H", "RESET_IP, 10, PT1H", "REGISTER_IP, 3, PT1H",
            "JOIN_IP, 5, PT1H", "JOIN_EMAIL, 3, PT24H", "REFRESH_IP, 60, PT1M", "PUBLIC_PAGE_IP, 120, PT1M", "LINK_TOKEN_IP, 30, PT1H"})
    void theTermsAreTheOnesOfTheThreatModel(RateLimitRule rule, int capacity, Duration window) {
        // Arrange
        // (the parameters)

        // Act
        int actualCapacity = rule.capacity();
        Duration actualWindow = rule.window();

        // Assert
        assertThat(actualCapacity).isEqualTo(capacity);
        assertThat(actualWindow).isEqualTo(window);
    }

    @Test
    void keysAreIndependent() {
        // Arrange
        useUp(RateLimitRule.LOGIN_EMAIL, "alice");

        // Act
        boolean allowed = allowed(RateLimitRule.LOGIN_EMAIL, "bob");

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void rulesAreIndependentForTheSameKey() {
        // Arrange
        useUp(RateLimitRule.LOGIN_EMAIL, "alice");

        // Act
        boolean allowed = allowed(RateLimitRule.RESET_EMAIL, "alice");

        // Assert
        assertThat(allowed).isTrue();
    }

    @Test
    void refillsOnceTheWindowHasPassedSoThereIsNoHardLock() {
        // Arrange
        RateLimitRule rule = RateLimitRule.LOGIN_EMAIL;
        useUp(rule, "key");
        boolean refusedBefore = !allowed(rule, "key");

        // Act
        clock.advance(rule.window().plusSeconds(1));
        boolean allowedAfter = allowed(rule, "key");

        // Assert
        assertThat(refusedBefore).isTrue();
        assertThat(allowedAfter).isTrue();
    }

    @Test
    void stillRefusedJustBeforeTheWindowEnds() {
        // Arrange
        RateLimitRule rule = RateLimitRule.LOGIN_EMAIL;
        useUp(rule, "key");
        clock.advance(rule.window().minusSeconds(5));
        Executable act = () -> limiter.check(rule, "key");

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, 6L);
    }

    @Test
    void emailKeysIgnoreCaseAndSpacesAndNeverContainTheAddress() {
        // Arrange
        String a = RateLimiter.emailKey("  Ana.Silva@Example.com ");
        String b = RateLimiter.emailKey("ana.silva@example.com");

        // Act
        String other = RateLimiter.emailKey("rita@example.com");

        // Assert
        assertThat(a).isEqualTo(b).isNotEqualTo(other).doesNotContain("ana").doesNotContain("@").hasSize(64);
    }

    @Test
    void joinKeysCombineTheAssociationAndTheEmail() {
        // Arrange
        String club = RateLimiter.joinKey("club-a", "ana@example.com");

        // Act
        String sameEmailOtherClub = RateLimiter.joinKey("club-b", "ana@example.com");

        // Assert
        assertThat(club).isNotEqualTo(sameEmailOtherClub).isEqualTo(RateLimiter.joinKey("club-a", " ANA@example.com"));
    }

    @Test
    void theExceptionNeverReportsLessThanOneSecond() {
        // Arrange
        RateLimitExceededException ex = new RateLimitExceededException(Duration.ofMillis(10));

        // Act
        long seconds = ex.retryAfterSeconds();

        // Assert
        assertThat(seconds).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "203.0.113.77, 203.0.113.77",
            "::ffff:203.0.113.77, 203.0.113.77",
            "2001:db8:abcd:12:1:2:3:4, v6:20010db8abcd0012",
            "2001:db8:abcd:12:ffff:ffff:ffff:ffff, v6:20010db8abcd0012",
            "2001:db8:abcd:13::1, v6:20010db8abcd0013"})
    void ipv4IsKeyedByItselfAndIpv6ByItsSlash64(String address, String expected) {
        // Arrange
        // (the parameters)

        // Act
        String key = RateLimiter.addressKey(address);

        // Assert
        assertThat(key).isEqualTo(expected);
    }

    @Test
    void addressesInsideOneIpv6SlashSixtyFourShareABucket() {
        // Arrange
        for (int i = 0; i < RateLimitRule.LOGIN_IP.capacity(); i++) {
            limiter.check(RateLimitRule.LOGIN_IP, RateLimiter.addressKey("2001:db8:abcd:12::" + (i + 1)));
        }
        Executable act = () -> limiter.check(RateLimitRule.LOGIN_IP, RateLimiter.addressKey("2001:db8:abcd:12:aaaa:bbbb:cccc:dddd"));

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isPositive();
    }

    @Test
    void aDifferentSlashSixtyFourHasItsOwnBucket() {
        // Arrange
        for (int i = 0; i < RateLimitRule.LOGIN_IP.capacity(); i++) {
            limiter.check(RateLimitRule.LOGIN_IP, RateLimiter.addressKey("2001:db8:abcd:12::" + (i + 1)));
        }

        // Act
        boolean allowed = allowed(RateLimitRule.LOGIN_IP, RateLimiter.addressKey("2001:db8:abcd:99::1"));

        // Assert
        assertThat(allowed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not an address", "evil.example.com", "999.1.1"})
    void anythingThatIsNotAnAddressLiteralIsNeverResolvedAndUsedAsItIs(String text) {
        // Arrange
        // (the parameter)

        // Act
        String key = RateLimiter.addressKey(text);

        // Assert
        assertThat(key).isEqualTo(text);
    }

    @Test
    void aMissingAddressDoesNotCrashTheKey() {
        // Arrange
        String missing = null;

        // Act
        String key = RateLimiter.addressKey(missing);

        // Assert
        assertThat(key).isEqualTo("null");
    }

    @Test
    void emailKeyedRulesRememberFewerKeysThanAddressKeyedOnes() {
        // Arrange
        long email = RateLimitRule.LOGIN_EMAIL.maxKeys();
        long address = RateLimitRule.LOGIN_IP.maxKeys();

        // Act
        boolean smaller = email < address;

        // Assert
        assertThat(smaller).isTrue();
        assertThat(email).isEqualTo(RateLimitRule.RESET_EMAIL.maxKeys()).isEqualTo(RateLimitRule.JOIN_EMAIL.maxKeys()).isEqualTo(20_000);
    }

    @Test
    void theLinkTokenRuleIsGenerousButBounded() {
        // Arrange
        RateLimitRule rule = RateLimitRule.LINK_TOKEN_IP;

        // Act
        int capacity = rule.capacity();

        // Assert
        assertThat(capacity).isEqualTo(30);
        assertThat(rule.window()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void worksOnTheProductionClockWhichTicksInMicroseconds() {
        // Arrange - ClockConfig's clock: Clock.tick(systemUTC, 1 microsecond); its millis() divides by zero
        RateLimiter onProductionClock = new RateLimiter(Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)));
        Executable beyondTheCapacity = () -> {
            for (int i = 0; i <= 5; i++) { // LOGIN_EMAIL allows 5 per window
                onProductionClock.check(RateLimitRule.LOGIN_EMAIL, "key");
            }
        };

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, beyondTheCapacity);

        // Assert
        assertThat(ex.retryAfterSeconds()).isPositive();
    }
}
