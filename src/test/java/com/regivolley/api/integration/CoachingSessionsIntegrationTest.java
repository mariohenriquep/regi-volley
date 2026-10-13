package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The staff's week (issue #48) over the whole stack, on a world shaped like the dev seed: the founder is administrator and coach of
 * the Wednesday group, a second coach runs a Friday group, a member holds a booking on the Wednesday. Counts, status and the three
 * action flags are read before the start, after attendance is marked and for a cancelled session; a coach sees only their own
 * sessions, an administrator all of them and a plain member none (403).
 */
class CoachingSessionsIntegrationTest extends AbstractApiIntegrationTest {

    private static final String WEEK = "/api/v1/coaching/sessions?weekOf=2026-10-14";
    /** Wednesday 14 October 2026, 20:05 in Lisbon: five minutes into the seeded session. */
    private static final Instant FIVE_MINUTES_IN = Instant.parse("2026-10-14T19:05:00Z");

    /** The seeded world plus a Friday 19:00 group coached by the second member, with its sessions generated. Returns the Friday group's id. */
    private String addFridayGroupOf(TenantWorld w) throws Exception {
        String group = read(expect(201, HttpMethod.POST, "/api/v1/training-groups", w.adminToken(), "{\"name\":\"Friday\",\"acceptedLevelIds\":[\""
                + w.entryLevelId() + "\"],\"venueId\":\"" + w.venueId() + "\",\"schedule\":[{\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"19:00\","
                + "\"durationMinutes\":60}],\"capacity\":8,\"coachId\":\"" + w.spare().memberId() + "\"}"), "$.id");
        generateSessionsFor(w.tenant());
        return group;
    }

    private String login(Person person) throws Exception {
        return read(expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", person.email(), "password", PASSWORD)), "$.accessToken");
    }

    @Test
    void anAdministratorSeesEverySessionOfTheWeekInStartOrderWithNamesAndCounts() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String friday = addFridayGroupOf(w);

        // Act
        MvcResult week = expect(200, HttpMethod.GET, WEEK, w.adminToken(), null);

        // Assert - Wednesday 20:00 then Friday 19:00 (Lisbon, summer time)
        assertThat((List<String>) read(week, "$[*].startsAt")).containsExactly("2026-10-14T19:00:00Z", "2026-10-16T18:00:00Z");
        assertThat((List<String>) read(week, "$[*].groupName")).containsExactly("Wednesday", "Friday");
        assertThat((List<String>) read(week, "$[*].venueName")).containsExactly("Pavilion", "Pavilion");
        assertThat((List<String>) read(week, "$[*].trainingGroupId")).containsExactly(w.groupId(), friday);
        assertThat((List<String>) read(week, "$[*].coachId")).containsExactly(w.adminId(), w.spare().memberId());
        assertThat((List<String>) read(week, "$[*].status")).containsOnly("SCHEDULED");
        assertThat((List<Integer>) read(week, "$[*].capacity")).containsExactly(12, 8);
        assertThat((List<Integer>) read(week, "$[*].confirmedCount")).containsExactly(1, 0);
        assertThat((List<Integer>) read(week, "$[*].unmarkedCount")).containsExactly(1, 0);
        assertThat((List<Integer>) read(week, "$[*].waitlistCount")).containsExactly(0, 0);
        assertThat((List<Integer>) read(week, "$[*].attendedCount")).containsExactly(0, 0);
        assertThat((List<Integer>) read(week, "$[*].noShowCount")).containsExactly(0, 0);
        assertThat(body(week)).doesNotContain("@").doesNotContain("Rita").doesNotContain(w.member().memberId());
    }

    @Test
    void aCoachSeesOnlyTheSessionsTheyCoach() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String friday = addFridayGroupOf(w);

        // Act
        MvcResult fridayCoach = expect(200, HttpMethod.GET, WEEK, w.spare().token(), null);
        MvcResult wednesdayCoach = expect(200, HttpMethod.GET, WEEK, w.adminToken(), null);
        MvcResult founderOnly = expect(200, HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2026-10-14", w.adminToken(), null);

        // Assert
        assertThat((List<String>) read(fridayCoach, "$[*].trainingGroupId")).containsExactly(friday);
        assertThat((List<String>) read(fridayCoach, "$[*].coachId")).containsExactly(w.spare().memberId());
        assertThat(body(fridayCoach)).doesNotContain(w.sessionId());
        assertThat((List<String>) read(wednesdayCoach, "$[*].sessionId")).contains(w.sessionId());
        assertThat((List<String>) read(founderOnly, "$[*].sessionId")).hasSize(2);
    }

    @Test
    void aPlainMemberIsRefusedAndAnAnonymousCallerIs401() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult member = send(HttpMethod.GET, WEEK, w.member().token());
        MvcResult anonymous = send(HttpMethod.GET, WEEK, null);

        // Assert
        assertThat(member.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) read(member, "$.code")).isEqualTo("NOT_ALLOWED");
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aCoachWhoseRoleWasRevokedIsRefusedAtOnce() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.GET, WEEK, w.spare().token(), null);
        expect(200, HttpMethod.PUT, "/api/v1/members/" + w.spare().memberId() + "/roles/MEMBER", w.adminToken(), null);
        expect(200, HttpMethod.DELETE, "/api/v1/members/" + w.spare().memberId() + "/roles/COACH", w.adminToken(), null);

        // Act
        MvcResult result = send(HttpMethod.GET, WEEK, w.spare().token());

        // Assert
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void aDeactivatedCoachCanNoLongerSignInToAskForTheirWeek() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.POST, "/api/v1/members/" + w.spare().memberId() + "/deactivation", w.adminToken(), null);

        // Act
        MvcResult result = send(HttpMethod.GET, WEEK, w.spare().token());

        // Assert - the principal is rejected before the use case: the token no longer names an active member
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void theFlagsAndCountsFollowTheSessionFromBeforeItsStartToAttendanceBeingMarked() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult before = expect(200, HttpMethod.GET, WEEK, w.adminToken(), null);
        clock.set(FIVE_MINUTES_IN);
        String coach = login(w.tenant().admin());
        MvcResult started = expect(200, HttpMethod.GET, WEEK, coach, null);
        expect(200, HttpMethod.PUT, "/api/v1/sessions/" + w.sessionId() + "/attendance", coach,
                "{\"entries\":[{\"bookingId\":\"" + w.bookingId() + "\",\"mark\":\"ATTENDED\"}]}");
        MvcResult marked = expect(200, HttpMethod.GET, WEEK, coach, null);

        // Assert
        assertThat((Boolean) read(before, "$[0].canMarkAttendance")).isFalse();
        assertThat((Boolean) read(before, "$[0].canCancel")).isTrue();
        assertThat((Boolean) read(before, "$[0].canChangeCapacity")).isTrue();
        assertThat((Boolean) read(started, "$[0].canMarkAttendance")).isTrue();
        assertThat((Boolean) read(started, "$[0].canCancel")).isTrue();
        assertThat((Boolean) read(started, "$[0].canChangeCapacity")).isFalse();
        assertThat((Integer) read(started, "$[0].unmarkedCount")).isEqualTo(1);
        assertThat((Integer) read(marked, "$[0].attendedCount")).isEqualTo(1);
        assertThat((Integer) read(marked, "$[0].unmarkedCount")).isZero();
        assertThat((Integer) read(marked, "$[0].confirmedCount")).isEqualTo(1);
        assertThat((Boolean) read(marked, "$[0].canMarkAttendance")).isFalse();
        assertThat((Boolean) read(marked, "$[0].canCancel")).isFalse();
        assertThat((Boolean) read(marked, "$[0].canChangeCapacity")).isFalse();
    }

    @Test
    void aCancelledSessionIsStillListedWithItsStatusZeroCountsAndNoActions() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/cancellation", w.adminToken(), "{\"reason\":\"Pavilion closed\"}");

        // Act
        MvcResult week = expect(200, HttpMethod.GET, WEEK, w.adminToken(), null);

        // Assert
        assertThat((List<String>) read(week, "$[*].sessionId")).containsExactly(w.sessionId());
        assertThat((String) read(week, "$[0].status")).isEqualTo("CANCELLED");
        assertThat((Integer) read(week, "$[0].confirmedCount")).isZero();
        assertThat((Integer) read(week, "$[0].unmarkedCount")).isZero();
        assertThat((Boolean) read(week, "$[0].canMarkAttendance")).isFalse();
        assertThat((Boolean) read(week, "$[0].canCancel")).isFalse();
        assertThat((Boolean) read(week, "$[0].canChangeCapacity")).isFalse();
    }

    @Test
    void theWeekIsTheLisbonWeekOfTheDateAskedForAndTheDateIsBounded() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult sunday = expect(200, HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2026-10-18", w.adminToken(), null);
        MvcResult monday = expect(200, HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2026-10-12", w.adminToken(), null);
        MvcResult nextWeek = expect(200, HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=2026-10-19", w.adminToken(), null);
        MvcResult thisWeek = expect(200, HttpMethod.GET, "/api/v1/coaching/sessions", w.adminToken(), null);
        MvcResult tooEarly = send(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=1999-12-31", w.adminToken());
        MvcResult malformed = send(HttpMethod.GET, "/api/v1/coaching/sessions?weekOf=tomorrow", w.adminToken());

        // Assert
        assertThat((List<String>) read(sunday, "$[*].sessionId")).containsExactly(w.sessionId());
        assertThat((List<String>) read(monday, "$[*].sessionId")).containsExactly(w.sessionId());
        assertThat((List<String>) read(thisWeek, "$[*].sessionId")).containsExactly(w.sessionId());
        assertThat((List<String>) read(nextWeek, "$[*].startsAt")).doesNotContain("2026-10-14T19:00:00Z");
        assertThat(tooEarly.getResponse().getStatus()).isEqualTo(400);
        assertThat(malformed.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void theRostersCoachingLineIsTheSameLineTheWeekListsFromBeforeTheStartToAttendanceBeingMarked() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String roster = "/api/v1/sessions/" + w.sessionId() + "/roster";

        // Act
        MvcResult before = expect(200, HttpMethod.GET, roster, w.adminToken(), null);
        MvcResult beforeWeek = expect(200, HttpMethod.GET, WEEK, w.adminToken(), null);
        clock.set(FIVE_MINUTES_IN);
        String coach = login(w.tenant().admin());
        MvcResult started = expect(200, HttpMethod.GET, roster, coach, null);
        MvcResult startedWeek = expect(200, HttpMethod.GET, WEEK, coach, null);
        expect(200, HttpMethod.PUT, "/api/v1/sessions/" + w.sessionId() + "/attendance", coach,
                "{\"entries\":[{\"bookingId\":\"" + w.bookingId() + "\",\"mark\":\"ATTENDED\"}]}");
        MvcResult marked = expect(200, HttpMethod.GET, roster, coach, null);
        MvcResult markedWeek = expect(200, HttpMethod.GET, WEEK, coach, null);

        // Assert
        assertThat((Object) read(before, "$.coaching")).isEqualTo(read(beforeWeek, "$[0]"));
        assertThat((Object) read(started, "$.coaching")).isEqualTo(read(startedWeek, "$[0]"));
        assertThat((Object) read(marked, "$.coaching")).isEqualTo(read(markedWeek, "$[0]"));
        assertThat((String) read(before, "$.coaching.groupName")).isEqualTo("Wednesday");
        assertThat((String) read(before, "$.coaching.venueName")).isEqualTo("Pavilion");
        assertThat((Boolean) read(before, "$.coaching.canMarkAttendance")).isFalse();
        assertThat((Boolean) read(before, "$.coaching.canChangeCapacity")).isTrue();
        assertThat((Boolean) read(started, "$.coaching.canMarkAttendance")).isTrue();
        assertThat((Integer) read(started, "$.coaching.unmarkedCount")).isEqualTo(1);
        assertThat((Boolean) read(marked, "$.coaching.canMarkAttendance")).isFalse();
        assertThat((Boolean) read(marked, "$.coaching.canCancel")).isFalse();
        assertThat((Integer) read(marked, "$.coaching.attendedCount")).isEqualTo(1);
    }

    @Test
    void aCancelledSessionsRosterOffersNoAction() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/cancellation", w.adminToken(), "{\"reason\":\"Pavilion closed\"}");

        // Act
        MvcResult roster = expect(200, HttpMethod.GET, "/api/v1/sessions/" + w.sessionId() + "/roster", w.adminToken(), null);

        // Assert
        assertThat((String) read(roster, "$.coaching.status")).isEqualTo("CANCELLED");
        assertThat((Boolean) read(roster, "$.coaching.canMarkAttendance")).isFalse();
        assertThat((Boolean) read(roster, "$.coaching.canCancel")).isFalse();
        assertThat((Boolean) read(roster, "$.coaching.canChangeCapacity")).isFalse();
    }

    @Test
    void meListsTheRolesAndFollowsAGrantAndARevocationOnTheSameToken() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String path = "/api/v1/me";

        // Act
        MvcResult member = expect(200, HttpMethod.GET, path, w.member().token(), null);
        MvcResult coach = expect(200, HttpMethod.GET, path, w.spare().token(), null);
        MvcResult founder = expect(200, HttpMethod.GET, path, w.adminToken(), null);
        expect(200, HttpMethod.DELETE, "/api/v1/members/" + w.spare().memberId() + "/roles/COACH", w.adminToken(), null);
        MvcResult revoked = expect(200, HttpMethod.GET, path, w.spare().token(), null);
        expect(200, HttpMethod.PUT, "/api/v1/members/" + w.member().memberId() + "/roles/ADMIN", w.adminToken(), null);
        MvcResult promoted = expect(200, HttpMethod.GET, path, w.member().token(), null);

        // Assert
        assertThat((List<String>) read(member, "$.roles")).containsExactly("MEMBER");
        assertThat((List<String>) read(coach, "$.roles")).containsExactly("MEMBER", "COACH");
        assertThat((List<String>) read(founder, "$.roles")).containsExactly("MEMBER", "COACH", "ADMIN");
        assertThat((List<String>) read(revoked, "$.roles")).containsExactly("MEMBER");
        assertThat((List<String>) read(promoted, "$.roles")).containsExactly("MEMBER", "ADMIN");
        assertThat((String) read(member, "$.memberId")).isEqualTo(w.member().memberId());
        assertThat((String) read(member, "$.associationId")).isEqualTo(w.tenant().associationId());
    }
}
