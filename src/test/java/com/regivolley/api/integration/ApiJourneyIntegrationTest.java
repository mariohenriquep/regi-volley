package com.regivolley.api.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * One journey through the whole API, the way a client would drive it, with no use case called directly except the scheduler's session
 * generation (it has no HTTP route): register an association, activate the founder from the mailed link, log in, set the association
 * up (level, venue, coach role, group, plan), a member asks to join, the administrator approves, the member activates, gets a plan and
 * pays, lists the week, books, cancels and logs out.
 */
class ApiJourneyIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void fromRegisteringAnAssociationToABookedAndCancelledSession() throws Exception {
        // Arrange
        String shortName = "journey-" + unique();
        String founderEmail = "founder." + unique() + "@example.com";

        // Act + Assert - the visitor registers; the answer carries nothing but the short name
        MvcResult registered = expect(201, HttpMethod.POST, "/api/v1/public/associations", null, registerBody(shortName, founderEmail));
        assertThat(body(registered)).isEqualTo("{\"shortName\":\"" + shortName + "\"}");
        assertThat(registered.getResponse().getHeader("Location")).isEqualTo("/api/v1/public/associations/" + shortName);

        // the founder cannot sign in before the mailed link is used, then can
        expect(401, HttpMethod.POST, "/api/v1/auth/login", null, json("email", founderEmail, "password", PASSWORD));
        String admin = activateAndLogin(founderEmail);
        MvcResult me = expect(200, HttpMethod.GET, "/api/v1/me", admin, null);
        String adminId = read(me, "$.memberId");

        // the association is set up: a third level, a venue, the coach role for the administrator, a group and a plan
        MvcResult levels = expect(201, HttpMethod.POST, "/api/v1/levels", admin, "{\"name\":\"Advanced\"}");
        String entryLevel = read(levels, "$.entryLevelId");
        assertThat(this.<List<String>>read(levels, "$.levels[*].name")).containsExactly("Beginner", "Intermediate", "Advanced");
        String venueId = read(expect(201, HttpMethod.POST, "/api/v1/venues", admin, "{\"name\":\"Pavilion One\",\"address\":\"Rua A 1, Lisbon\",\"courts\":2}"), "$.id");
        expect(200, HttpMethod.PUT, "/api/v1/members/" + adminId + "/roles/COACH", admin, null);
        MvcResult group = expect(201, HttpMethod.POST, "/api/v1/training-groups", admin, "{\"name\":\"Wednesday Beginners\",\"acceptedLevelIds\":[\""
                + entryLevel + "\"],\"venueId\":\"" + venueId + "\",\"schedule\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"20:00\","
                + "\"durationMinutes\":90}],\"capacity\":12,\"coachId\":\"" + adminId + "\"}");
        assertThat(this.<String>read(group, "$.status")).isEqualTo("ACTIVE");
        MvcResult plan = expect(201, HttpMethod.POST, "/api/v1/plans", admin, "{\"name\":\"Ten sessions\",\"type\":\"PACK\",\"credits\":10,"
                + "\"allowedLevelIds\":[\"" + entryLevel + "\"],\"priceCents\":4500,\"validityDays\":90}");
        String planId = read(plan, "$.id");

        // anyone can read the public page: levels, groups with schedules, venues, contacts
        MvcResult page = expect(200, HttpMethod.GET, "/api/v1/public/associations/" + shortName, null, null);
        assertThat(this.<List<String>>read(page, "$.levels")).containsExactly("Beginner", "Intermediate", "Advanced");
        assertThat(this.<String>read(page, "$.groups[0].name")).isEqualTo("Wednesday Beginners");
        assertThat(this.<String>read(page, "$.groups[0].venue")).isEqualTo("Pavilion One");
        assertThat(this.<String>read(page, "$.groups[0].schedule[0].startTime")).isEqualTo("20:00");
        assertThat(this.<String>read(page, "$.contactEmail")).isEqualTo("info@" + shortName + ".example");
        assertThat(body(page)).doesNotContain(adminId).doesNotContain(founderEmail);

        // a visitor asks to join; the administrator sees the request, approves it; the member activates and signs in
        String memberEmail = "rita." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + shortName + "/join-requests", null, joinBody("Rita Costa", memberEmail));
        MvcResult pending = expect(200, HttpMethod.GET, "/api/v1/join-requests", admin, null);
        assertThat(this.<String>read(pending, "$[0].status")).isEqualTo("PENDING");
        String requestId = read(pending, "$[0].id");
        MvcResult approval = expect(200, HttpMethod.POST, "/api/v1/join-requests/" + requestId + "/approval", admin, null);
        String memberId = read(approval, "$.memberId");
        assertThat(this.<List<?>>read(expect(200, HttpMethod.GET, "/api/v1/join-requests", admin, null), "$")).isEmpty();
        expect(401, HttpMethod.POST, "/api/v1/auth/login", null, json("email", memberEmail, "password", PASSWORD));
        String member = activateAndLogin(memberEmail);

        // the member has no plan yet; the administrator gives them one and records the payment in full
        assertThat(this.<List<?>>read(expect(200, HttpMethod.GET, "/api/v1/me/plan", member, null), "$.subscriptions")).isEmpty();
        MvcResult subscription = expect(201, HttpMethod.POST, "/api/v1/members/" + memberId + "/subscriptions", admin,
                "{\"planId\":\"" + planId + "\",\"startDate\":\"2026-10-12\"}");
        String subscriptionId = read(subscription, "$.id");
        MvcResult payment = expect(201, HttpMethod.POST, "/api/v1/subscriptions/" + subscriptionId + "/payments", admin,
                "{\"amountCents\":4500,\"paidOn\":\"2026-10-12\",\"method\":\"MB_WAY\"}");
        assertThat(this.<String>read(payment, "$.paymentStatus")).isEqualTo("PAID");
        assertThat(this.<Integer>read(payment, "$.outstandingCents")).isZero();

        // the scheduler generates the sessions; the member lists the week and books
        generateSessionsFor(new Tenant(shortName, read(me, "$.associationId"), null));
        MvcResult week = expect(200, HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14", member, null);
        assertThat(this.<List<?>>read(week, "$")).hasSize(1);
        assertThat(this.<String>read(week, "$[0].groupName")).isEqualTo("Wednesday Beginners");
        assertThat(this.<String>read(week, "$[0].startsAt")).isEqualTo("2026-10-14T19:00:00Z");
        assertThat(this.<Integer>read(week, "$[0].freeSeats")).isEqualTo(12);
        String sessionId = read(week, "$[0].sessionId");
        MvcResult booked = expect(201, HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/bookings", member, null);
        assertThat(this.<String>read(booked, "$.status")).isEqualTo("CONFIRMED");
        String bookingId = read(booked, "$.bookingId");
        assertThat(this.<String>read(expect(200, HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14", member, null), "$[0].myBookingStatus"))
                .isEqualTo("CONFIRMED");
        assertThat(this.<Integer>read(expect(200, HttpMethod.GET, "/api/v1/me/plan", member, null), "$.subscriptions[0].remaining")).isEqualTo(9);

        // booking twice is a conflict, not a second seat
        expect(409, HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/bookings", member, null);

        // the member cancels in time: the credit comes back
        MvcResult cancelled = expect(200, HttpMethod.POST, "/api/v1/sessions/" + sessionId + "/bookings/" + bookingId + "/cancellation", member, null);
        assertThat(this.<Boolean>read(cancelled, "$.late")).isFalse();
        assertThat(this.<Boolean>read(cancelled, "$.creditRefunded")).isTrue();
        assertThat(this.<Integer>read(expect(200, HttpMethod.GET, "/api/v1/me/plan", member, null), "$.subscriptions[0].remaining")).isEqualTo(10);

        // the administrator's views agree: nothing is overdue, the paid subscription is listed, the CSV has it
        assertThat(this.<List<?>>read(expect(200, HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=OVERDUE", admin, null), "$")).isEmpty();
        assertThat(this.<String>read(expect(200, HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PAID", admin, null), "$[0].memberName")).isEqualTo("Rita Costa");
        MvcResult csv = expect(200, HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PAID", admin, null);
        assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Rita Costa").contains(subscriptionId);

        // the member signs in again, keeps the refresh cookie, logs out; the cookie is dead afterwards
        MvcResult login = expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", memberEmail, "password", PASSWORD));
        String refreshToken = cookieValue(login);
        mockMvc.perform(post("/api/v1/auth/logout").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON).cookie(new Cookie("__Secure-rt", refreshToken))
                .content("{}")).andReturn();
        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("__Secure-rt", refreshToken)).content("{}")).andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(401);
        assertThat(URLEncoder.encode(refreshToken, StandardCharsets.UTF_8)).isNotBlank();
    }

    @Test
    void anAdministratorCanReverseAPaymentAndMarkASubscriptionOverdue() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String adminToken = tenant.admin().token();
        String entryLevel = read(expect(201, HttpMethod.POST, "/api/v1/levels", adminToken, "{\"name\":\"Advanced\"}"), "$.entryLevelId");
        String planId = read(expect(201, HttpMethod.POST, "/api/v1/plans", adminToken,
                "{\"name\":\"Monthly\",\"type\":\"MONTHLY_UNLIMITED\",\"allowedLevelIds\":[\"" + entryLevel + "\"],\"priceCents\":3000}"), "$.id");
        Person member = joinAndActivate(tenant, "Rita Costa");
        String subscriptionId = read(expect(201, HttpMethod.POST, "/api/v1/members/" + member.memberId() + "/subscriptions", adminToken,
                "{\"planId\":\"" + planId + "\",\"startDate\":\"2026-10-12\"}"), "$.id");
        String paymentId = read(expect(201, HttpMethod.POST, "/api/v1/subscriptions/" + subscriptionId + "/payments", adminToken,
                "{\"amountCents\":3000,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}"), "$.payment.id");

        // Act
        MvcResult reversed = expect(201, HttpMethod.POST, "/api/v1/payments/" + paymentId + "/reversal", adminToken, null);
        MvcResult overdue = expect(200, HttpMethod.POST, "/api/v1/subscriptions/" + subscriptionId + "/overdue-marking", adminToken, null);
        expect(422, HttpMethod.POST, "/api/v1/payments/" + paymentId + "/reversal", adminToken, null);

        // Assert
        assertThat(this.<String>read(reversed, "$.reversal.reversalOf")).isEqualTo(paymentId);
        assertThat(this.<String>read(reversed, "$.paymentStatus")).isEqualTo("PENDING");
        assertThat(this.<Integer>read(reversed, "$.outstandingCents")).isEqualTo(3000);
        assertThat(this.<String>read(overdue, "$.paymentStatus")).isEqualTo("OVERDUE");
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        Matcher matcher = Pattern.compile("__Secure-rt=([^;]*);").matcher(header);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
