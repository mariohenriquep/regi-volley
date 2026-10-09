package com.regivolley.api.infrastructure.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.regivolley.api.application.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * In-memory token buckets (bucket4j) per {@link RateLimitRule} and key, held in a bounded, expiring Caffeine cache per rule so
 * a flood of distinct keys cannot grow the heap without limit (threat model D-9). The app is one container, so no shared store
 * is needed yet; a restart resets the counters and a second instance would need a shared one (risk R5). Time comes from the
 * injected {@link Clock}. Keys that identify a person are hashed first ({@link #emailKey}): nothing personal is kept.
 */
public class RateLimiter {

    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern IPV6_CHARACTERS = Pattern.compile("[0-9a-fA-F:.]+");

    private final Map<RateLimitRule, Cache<String, Bucket>> buckets = new EnumMap<>(RateLimitRule.class);
    private final TimeMeter timeMeter;

    public RateLimiter(Clock clock) {
        this.timeMeter = new ClockTimeMeter(clock);
        for (RateLimitRule rule : RateLimitRule.values()) {
            buckets.put(rule, Caffeine.newBuilder().maximumSize(rule.maxKeys()).expireAfterAccess(rule.window()).build());
        }
    }

    /**
     * Takes one token for the key.
     *
     * @throws RateLimitExceededException if the bucket is empty; its {@code retryAfterSeconds} says when a token is back
     */
    public void check(RateLimitRule rule, String key) {
        Bucket bucket = buckets.get(rule).get(key, k -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        }
    }

    /**
     * The key for per-IP limits: an IPv4 address by itself (/32), an IPv6 address by its /64 (a single subscriber holds at least a
     * /64, so rotating through the addresses inside it must not give a fresh bucket), an IPv4-mapped IPv6 address as the IPv4 it
     * carries. Anything that is not an address literal is used as it is (it cannot come from a socket). Audit lines truncate more
     * ({@code ClientAddresses}).
     */
    public static String addressKey(String address) {
        if (address == null) {
            return "null";
        }
        if (IPV4.matcher(address).matches()) {
            return address;
        }
        if (address.indexOf(':') < 0 || !IPV6_CHARACTERS.matcher(address).matches()) {
            return address;
        }
        try {
            // Only strings with a colon reach this: they are parsed as IPv6 literals and never looked up in DNS.
            byte[] bytes = InetAddress.getByName(address).getAddress();
            if (bytes.length == 4) {
                return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
            }
            return "v6:" + HexFormat.of().formatHex(bytes, 0, 8);
        } catch (UnknownHostException e) {
            return address;
        }
    }

    /** The key for per-email limits: SHA-256 of the trimmed, lower-cased address. */
    public static String emailKey(String email) {
        return SecureTokens.sha256(normalise(email));
    }

    /** The key for the per-(association, email) join limit. */
    public static String joinKey(String shortName, String email) {
        return SecureTokens.sha256(normalise(shortName) + '\n' + normalise(email));
    }

    private Bucket newBucket(RateLimitRule rule) {
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit -> limit.capacity(rule.capacity()).refillIntervally(rule.capacity(), rule.window()))
                .build();
    }

    private static String normalise(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** bucket4j's time source on our clock, so tests can move time. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            return Math.multiplyExact(clock.millis(), 1_000_000L);
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
