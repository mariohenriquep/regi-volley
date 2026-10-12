package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #57 over the whole stack: a refused booking or cancellation carries a stable {@code reason} next to the unchanged
 * {@code code}, taken from the domain (never from the message), and the session list gives the caller what they need to cancel.
 */
class BookingRefusalReasonIntegrationTest extends AbstractApiIntegrationTest {

    /** Wednesday 14 October 2026, 20:05 in Lisbon: five minutes into the seeded session. */
    private static final Instant FIVE_MINUTES_IN = Instant.parse("2026-10-14T19:05:00Z");

    private MvcResult book(TenantWorld world, String token) throws Exception {
        return send(HttpMethod.POST, "/api/v1/sessions/" + world.sessionId() + "/bookings", token);
    }

    /** The tokens of the set-up expire when the clock jumps to the session: the person signs in again. */
    private String signInAgain(Person person) throws Exception {
        return read(expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", person.email(), "password", PASSWORD)), "$.accessToken");
    }

    @Test
    void aMemberWithoutAnySubscriptionIsRefusedWithNoValidSubscription() throws Exception {
        // Arrange - the second member has no plan
        TenantWorld world = seedWorld();

        // Act
        MvcResult refused = book(world, world.spare().token());

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(422);
        assertThat(read(refused, "$.code").toString()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(read(refused, "$.reason").toString()).isEqualTo("NO_VALID_SUBSCRIPTION");
    }

    @Test
    void bookingTwiceIsAConflictWithTheReasonDuplicateBooking() throws Exception {
        // Arrange - the member already holds the seeded booking
        TenantWorld world = seedWorld();

        // Act
        MvcResult refused = book(world, world.member().token());

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(refused, "$.code").toString()).isEqualTo("CONFLICT");
        assertThat(read(refused, "$.reason").toString()).isEqualTo("DUPLICATE_BOOKING");
    }

    @Test
    void bookingAfterTheStartIsRefusedWithBookingClosed() throws Exception {
        // Arrange
        TenantWorld world = seedWorld();
        expect(201, HttpMethod.POST, "/api/v1/members/" + world.spare().memberId() + "/subscriptions", world.adminToken(),
                "{\"planId\":\"" + world.planId() + "\",\"startDate\":\"2026-10-12\"}");
        clock.set(FIVE_MINUTES_IN);
        String token = signInAgain(world.spare());

        // Act
        MvcResult refused = book(world, token);

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(422);
        assertThat(read(refused, "$.reason").toString()).isEqualTo("BOOKING_CLOSED");
    }

    @Test
    void cancellingAfterTheStartIsRefusedWithCancellationClosed() throws Exception {
        // Arrange
        TenantWorld world = seedWorld();
        clock.set(FIVE_MINUTES_IN);
        String token = signInAgain(world.member());

        // Act
        MvcResult refused = send(HttpMethod.POST, "/api/v1/sessions/" + world.sessionId() + "/bookings/" + world.bookingId() + "/cancellation", token);

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(422);
        assertThat(read(refused, "$.reason").toString()).isEqualTo("CANCELLATION_CLOSED");
    }

    @Test
    void theSessionListGivesTheBookingIdToCancelWithAndTheDeadlineThatMakesItFree() throws Exception {
        // Arrange - 14 Oct 20:00 Lisbon (UTC+1) is 19:00Z, so free cancellation ends at 13:00Z
        TenantWorld world = seedWorld();

        // Act
        MvcResult week = expect(200, HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14", world.member().token(), null);
        String line = "$[?(@.sessionId == '" + world.sessionId() + "')]";

        // Assert
        assertThat(this.<java.util.List<Object>>read(week, line + ".myBookingId")).containsExactly(world.bookingId());
        assertThat(this.<java.util.List<Object>>read(week, line + ".freeCancellationUntil")).containsExactly("2026-10-14T13:00:00Z");
        assertThat(this.<java.util.List<Object>>read(week, line + ".venueName")).containsExactly("Pavilion");
        MvcResult cancelled = send(HttpMethod.POST, "/api/v1/sessions/" + world.sessionId() + "/bookings/" + world.bookingId() + "/cancellation",
                world.member().token());
        assertThat(cancelled.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(cancelled, "$.late").toString()).isEqualTo("false");
    }

    @Test
    void afterTheSessionTheHistoryNamesTheGroupAndTheVenue() throws Exception {
        // Arrange
        TenantWorld world = seedWorld();
        clock.set(Instant.parse("2026-10-14T21:00:00Z"));
        String token = signInAgain(world.member());

        // Act
        MvcResult history = expect(200, HttpMethod.GET, "/api/v1/me/history", token, null);

        // Assert
        assertThat(read(history, "$.entries[0].trainingGroupName").toString()).isEqualTo("Wednesday");
        assertThat(read(history, "$.entries[0].venueName").toString()).isEqualTo("Pavilion");
    }
}
