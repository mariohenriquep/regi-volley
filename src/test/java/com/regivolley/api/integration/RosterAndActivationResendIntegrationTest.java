package com.regivolley.api.integration;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #38 over the whole stack: the coach reads the roster and marks attendance with the ids it returned, and an administrator
 * re-sends an activation link that supersedes the old one and activates the account.
 */
class RosterAndActivationResendIntegrationTest extends AbstractApiIntegrationTest {

    /** Wednesday 14 October 2026, 20:05 in Lisbon: five minutes into the seeded session. */
    private static final Instant FIVE_MINUTES_IN = Instant.parse("2026-10-14T19:05:00Z");

    /** A visitor asks to join and the administrator approves; the new member has an account that is not activated. Returns the member id. */
    private String approvedButNotActivated(Tenant tenant, String name, String email) throws Exception {
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody(name, email));
        MvcResult pending = expect(200, HttpMethod.GET, "/api/v1/join-requests", tenant.admin().token(), null);
        List<String> ids = read(pending, "$[?(@.email == '" + email + "')].id");
        MvcResult approval = expect(200, HttpMethod.POST, "/api/v1/join-requests/" + ids.get(0) + "/approval", tenant.admin().token(), null);
        return read(approval, "$.memberId");
    }

    private String resendPath(String memberId) {
        return "/api/v1/members/" + memberId + "/activation-links";
    }

    @Test
    void theCoachReadsTheRosterThenMarksAttendanceWithTheIdsItReturned() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(201, HttpMethod.POST, "/api/v1/members/" + w.spare().memberId() + "/subscriptions", w.adminToken(),
                "{\"planId\":\"" + w.planId() + "\",\"startDate\":\"2026-10-12\"}");
        expect(201, HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/bookings", w.spare().token(), null);
        clock.set(FIVE_MINUTES_IN);
        // the tokens of the set-up have expired by now: the coach (the founder) signs in again
        String coach = read(expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", w.tenant().admin().email(), "password", PASSWORD)),
                "$.accessToken");

        // Act
        MvcResult roster = expect(200, HttpMethod.GET, "/api/v1/sessions/" + w.sessionId() + "/roster", coach, null);
        List<String> ritasBooking = read(roster, "$.seats[?(@.memberId == '" + w.member().memberId() + "')].bookingId");
        List<String> paulosBooking = read(roster, "$.seats[?(@.memberId == '" + w.spare().memberId() + "')].bookingId");
        MvcResult marked = expect(200, HttpMethod.PUT, "/api/v1/sessions/" + w.sessionId() + "/attendance", coach,
                "{\"entries\":[{\"bookingId\":\"" + ritasBooking.get(0) + "\",\"mark\":\"ATTENDED\"},{\"bookingId\":\"" + paulosBooking.get(0)
                        + "\",\"mark\":\"NO_SHOW\"}]}");
        MvcResult after = expect(200, HttpMethod.GET, "/api/v1/sessions/" + w.sessionId() + "/roster", coach, null);

        // Assert
        assertThat(ritasBooking).isEqualTo(List.of(w.bookingId()));
        assertThat((String) read(roster, "$.sessionId")).isEqualTo(w.sessionId());
        assertThat((String) read(roster, "$.coachId")).isEqualTo(w.adminId());
        assertThat((List<String>) read(roster, "$.seats[*].memberName")).containsExactlyInAnyOrder("Rita Costa", "Paulo Dias");
        assertThat((List<String>) read(roster, "$.seats[*].status")).containsOnly("CONFIRMED");
        assertThat((List<String>) read(roster, "$.waitlist")).isEmpty();
        assertThat(body(roster)).doesNotContain("@").doesNotContain("912345679").doesNotContain("email").doesNotContain("phone");
        assertThat((List<String>) read(marked, "$.session.bookings[*].status")).containsExactlyInAnyOrder("ATTENDED", "NO_SHOW");
        assertThat((List<String>) read(after, "$.seats[?(@.memberId == '" + w.member().memberId() + "')].status")).containsExactly("ATTENDED");
        assertThat((List<String>) read(after, "$.seats[?(@.memberId == '" + w.spare().memberId() + "')].status")).containsExactly("NO_SHOW");
    }

    @Test
    void aPlainMemberAndACoachOfAnotherSessionCannotReadTheRoster() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String path = "/api/v1/sessions/" + w.sessionId() + "/roster";

        // Act
        MvcResult member = send(HttpMethod.GET, path, w.member().token());
        MvcResult otherCoach = send(HttpMethod.GET, path, w.spare().token());
        MvcResult noToken = send(HttpMethod.GET, path, null);

        // Assert
        assertThat(member.getResponse().getStatus()).isEqualTo(403);
        assertThat(otherCoach.getResponse().getStatus()).isEqualTo(403);
        assertThat(noToken.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(member)).doesNotContain("Rita Costa");
    }

    @Test
    void theResentLinkSupersedesTheOldOneIsMailedToTheAccountAndActivatesIt() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String email = "ines.lopes." + unique() + "@example.com";
        String memberId = approvedButNotActivated(tenant, "Ines Lopes", email);
        AccountLink first = sentLinkTo(email, AccountLinkPurpose.ACTIVATION);
        clock.advance(java.time.Duration.ofHours(1));
        // the founder's token has expired with the hour that passed
        String admin = read(expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", tenant.admin().email(), "password", PASSWORD)),
                "$.accessToken");
        Mockito.clearInvocations(mailer);

        // Act
        MvcResult resent = expect(202, HttpMethod.POST, resendPath(memberId), admin, null);
        AccountLink second = sentLinkTo(email, AccountLinkPurpose.ACTIVATION);
        MvcResult oldLink = send(HttpMethod.POST, "/api/v1/auth/activate", null, json("token", first.token(), "password", PASSWORD));
        MvcResult newLink = send(HttpMethod.POST, "/api/v1/auth/activate", null, json("token", second.token(), "password", PASSWORD));
        MvcResult login = send(HttpMethod.POST, "/api/v1/auth/login", null, json("email", email, "password", PASSWORD));

        // Assert
        assertThat(body(resent)).isEqualTo("{\"status\":\"RECEIVED\"}");
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(oldLink.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(oldLink)).contains("INVALID_LINK");
        assertThat(newLink.getResponse().getStatus()).isEqualTo(204);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theAnswerIsTheSameForAMemberWhoAlreadyActivatedAndNothingIsSent() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String email = "ines.lopes." + unique() + "@example.com";
        String pendingMember = approvedButNotActivated(tenant, "Ines Lopes", email);
        Person activated = joinAndActivate(tenant, "Rita Costa");
        Mockito.clearInvocations(mailer);

        // Act
        MvcResult forPending = send(HttpMethod.POST, resendPath(pendingMember), tenant.admin().token());
        int sentForPending = Mockito.mockingDetails(mailer).getInvocations().size();
        MvcResult forActivated = send(HttpMethod.POST, resendPath(activated.memberId()), tenant.admin().token());

        // Assert
        assertThat(forPending.getResponse().getStatus()).isEqualTo(202);
        assertThat(forActivated.getResponse().getStatus()).isEqualTo(202);
        assertThat(body(forActivated)).isEqualTo(body(forPending));
        assertThat(sentForPending).isEqualTo(1);
        assertThat(Mockito.mockingDetails(mailer).getInvocations()).hasSize(1);
    }

    @Test
    void aMemberWhoseProvisioningNeverHappenedIsProvisionedAgain() throws Exception {
        // Arrange - P7: the account step failed after the member was committed, so there is no membership and no link. Failed
        // provisioning cannot be produced over HTTP (the real step succeeds), so the state it leaves is set up with JDBC.
        Tenant tenant = registerTenant();
        String email = "ines.lopes." + unique() + "@example.com";
        String memberId = approvedButNotActivated(tenant, "Ines Lopes", email);
        jdbc.update("delete from email_link where membership_id in (select id from membership where member_id = ?::uuid)", memberId);
        jdbc.update("delete from membership where member_id = ?::uuid", memberId);
        Mockito.clearInvocations(mailer);

        // Act
        expect(202, HttpMethod.POST, resendPath(memberId), tenant.admin().token(), null);
        String token = activateAndLogin(email);

        // Assert
        assertThat(token).isNotBlank();
    }

    @Test
    void theFourthRequestForOneMemberInAnHourIs429WithRetryAfter() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String memberId = approvedButNotActivated(tenant, "Ines Lopes", "ines.lopes." + unique() + "@example.com");
        for (int i = 0; i < 3; i++) {
            expect(202, HttpMethod.POST, resendPath(memberId), tenant.admin().token(), null);
        }
        Mockito.clearInvocations(mailer);

        // Act
        MvcResult refused = send(HttpMethod.POST, resendPath(memberId), tenant.admin().token());

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(refused.getResponse().getHeader("Retry-After"))).isBetween(1L, 3600L);
        Mockito.verifyNoInteractions(mailer);
    }

    @Test
    void onlyAnAdministratorMayAskAndAPlainMemberGetsNothingSent() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String memberId = approvedButNotActivated(tenant, "Ines Lopes", "ines.lopes." + unique() + "@example.com");
        Person member = joinAndActivate(tenant, "Rita Costa");
        Mockito.clearInvocations(mailer);

        // Act
        MvcResult refused = send(HttpMethod.POST, resendPath(memberId), member.token());

        // Assert
        assertThat(refused.getResponse().getStatus()).isEqualTo(403);
        Mockito.verifyNoInteractions(mailer);
    }

    @Test
    void anIdOfAnotherAssociationAndOneThatExistsNowhereSpendTheSameBudgetAsARealMember() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        Tenant other = registerTenant();
        String ghost = UUID.randomUUID().toString();
        String foreign = other.admin().memberId();

        // Act
        List<Integer> ghostAnswers = new java.util.ArrayList<>();
        List<Integer> foreignAnswers = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ghostAnswers.add(send(HttpMethod.POST, resendPath(ghost), tenant.admin().token()).getResponse().getStatus());
            foreignAnswers.add(send(HttpMethod.POST, resendPath(foreign), tenant.admin().token()).getResponse().getStatus());
        }

        // Assert - the first three are 404 for both and the fourth is a 429 for both: the throttle cannot tell them apart
        assertThat(ghostAnswers).containsExactly(404, 404, 404, 429);
        assertThat(foreignAnswers).isEqualTo(ghostAnswers);
    }

    @Test
    void oneMailboxListedByManyAssociationsCannotBeMailedMoreThanSixTimesAnHourBetweenThem() throws Exception {
        // Arrange - the same address is a member of three associations, none of them activated
        String email = "ines.lopes." + unique() + "@example.com";
        Tenant first = registerTenant();
        Tenant second = registerTenant();
        Tenant third = registerTenant();
        String inFirst = approvedButNotActivated(first, "Ines Lopes", email);
        String inSecond = approvedButNotActivated(second, "Ines Lopes", email);
        String inThird = approvedButNotActivated(third, "Ines Lopes", email);
        Mockito.clearInvocations(mailer);
        for (int i = 0; i < 3; i++) {
            expect(202, HttpMethod.POST, resendPath(inFirst), first.admin().token(), null);
            expect(202, HttpMethod.POST, resendPath(inSecond), second.admin().token(), null);
        }

        // Act
        MvcResult seventh = send(HttpMethod.POST, resendPath(inThird), third.admin().token());

        // Assert
        assertThat(seventh.getResponse().getStatus()).isEqualTo(429);
        assertThat(Mockito.mockingDetails(mailer).getInvocations()).hasSize(6);
    }
}
