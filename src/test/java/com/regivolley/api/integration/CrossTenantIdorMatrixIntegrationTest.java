package com.regivolley.api.integration;

import com.regivolley.api.infrastructure.security.PublicRoutes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threat model 10.5 and 10.6, over the real filter chain, controllers, services and PostgreSQL: two associations are seeded with one
 * of everything, and for every authenticated route
 * <ul>
 *   <li>a valid token of association A used with association B's ids gets the answer a non-existent id gets - 404, never a 200 or a
 *       403 that would reveal the id exists - and nothing changes in either association (a fingerprint of every business table);</li>
 *   <li>the list routes return only the caller's own data;</li>
 *   <li>a plain member of A gets 403 on every administrator or staff route, with A's own ids (so it is the role that refuses).</li>
 * </ul>
 * A route that is added without a row here fails {@link #everyAuthenticatedRouteHasARowHere}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CrossTenantIdorMatrixIntegrationTest extends AbstractApiIntegrationTest {

    /** Who may call the route: anyone, an administrator, the staff of the session (its coach or an administrator), or an administrator or any coach. */
    private enum Caller { MEMBER, ADMIN, STAFF, ADMIN_OR_COACH }

    private record Row(String route, Caller caller, String variant, BiFunction<TenantWorld, TenantWorld, String> path,
                       BiFunction<TenantWorld, TenantWorld, String> body, int foreignStatus) {
        HttpMethod method() {
            return HttpMethod.valueOf(route.split(" ", 2)[0]);
        }

        @Override
        public String toString() {
            return route + (variant.isEmpty() ? "" : " (" + variant + ")");
        }
    }

    private static Row row(String route, Caller caller, String variant, BiFunction<TenantWorld, TenantWorld, String> path,
                           BiFunction<TenantWorld, TenantWorld, String> body, int foreignStatus) {
        return new Row(route, caller, variant, path, body, foreignStatus);
    }

    private static Row row(String route, Caller caller, BiFunction<TenantWorld, TenantWorld, String> path, BiFunction<TenantWorld, TenantWorld, String> body) {
        return row(route, caller, "", path, body, 404);
    }

    private static final String NO_BODY = null;

    private static final List<Row> ROWS = List.of(
            // the member's own bookings
            row("POST /api/v1/sessions/{sessionId}/bookings", Caller.MEMBER, (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/bookings", (own, other) -> NO_BODY),
            row("POST /api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation", Caller.MEMBER, "session and booking of another association",
                    (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/bookings/" + other.bookingId() + "/cancellation", (own, other) -> NO_BODY, 404),
            row("POST /api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation", Caller.MEMBER, "own session, booking of another association",
                    (own, other) -> "/api/v1/sessions/" + own.sessionId() + "/bookings/" + other.bookingId() + "/cancellation", (own, other) -> NO_BODY, 404),
            row("POST /api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation", Caller.MEMBER, "session of another association, own booking",
                    (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/bookings/" + own.bookingId() + "/cancellation", (own, other) -> NO_BODY, 404),
            // the staff of a session
            row("POST /api/v1/sessions/{sessionId}/cancellation", Caller.STAFF, (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/cancellation",
                    (own, other) -> "{\"reason\":\"closed\"}"),
            row("PUT /api/v1/sessions/{sessionId}/capacity", Caller.STAFF, (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/capacity",
                    (own, other) -> "{\"capacity\":5}"),
            row("PUT /api/v1/sessions/{sessionId}/attendance", Caller.STAFF, "session and booking of another association",
                    (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/attendance",
                    (own, other) -> "{\"entries\":[{\"bookingId\":\"" + other.bookingId() + "\",\"mark\":\"ATTENDED\"}]}", 404),
            row("PUT /api/v1/sessions/{sessionId}/attendance", Caller.STAFF, "own session, booking of another association",
                    (own, other) -> "/api/v1/sessions/" + own.sessionId() + "/attendance",
                    (own, other) -> "{\"entries\":[{\"bookingId\":\"" + other.bookingId() + "\",\"mark\":\"ATTENDED\"}]}", 422),
            row("PUT /api/v1/sessions/{sessionId}/attendance", Caller.STAFF, "session of another association, own booking",
                    (own, other) -> "/api/v1/sessions/" + other.sessionId() + "/attendance",
                    (own, other) -> "{\"entries\":[{\"bookingId\":\"" + own.bookingId() + "\",\"mark\":\"ATTENDED\"}]}", 404),
            // levels
            row("PUT /api/v1/levels/{levelId}", Caller.ADMIN, (own, other) -> "/api/v1/levels/" + other.secondLevelId(), (own, other) -> "{\"name\":\"Renamed\"}"),
            row("PUT /api/v1/levels/order", Caller.ADMIN, "a level of another association", (own, other) -> "/api/v1/levels/order",
                    (own, other) -> "{\"levelIds\":[\"" + own.entryLevelId() + "\",\"" + other.secondLevelId() + "\",\"" + own.thirdLevelId() + "\"]}", 422),
            row("PUT /api/v1/levels/entry-level", Caller.ADMIN, (own, other) -> "/api/v1/levels/entry-level", (own, other) -> "{\"levelId\":\"" + other.secondLevelId() + "\"}"),
            // venues
            row("PUT /api/v1/venues/{venueId}", Caller.ADMIN, (own, other) -> "/api/v1/venues/" + other.venueId(),
                    (own, other) -> "{\"name\":\"Renamed\",\"address\":\"Rua B 2\",\"courts\":3}"),
            row("DELETE /api/v1/venues/{venueId}", Caller.ADMIN, (own, other) -> "/api/v1/venues/" + other.venueId(), (own, other) -> NO_BODY),
            // training groups
            row("POST /api/v1/training-groups", Caller.ADMIN, "venue of another association", (own, other) -> "/api/v1/training-groups",
                    (own, other) -> groupBody(own.entryLevelId(), other.venueId(), own.adminId()), 404),
            row("POST /api/v1/training-groups", Caller.ADMIN, "level of another association", (own, other) -> "/api/v1/training-groups",
                    (own, other) -> groupBody(other.entryLevelId(), own.venueId(), own.adminId()), 404),
            row("POST /api/v1/training-groups", Caller.ADMIN, "coach of another association", (own, other) -> "/api/v1/training-groups",
                    (own, other) -> groupBody(own.entryLevelId(), own.venueId(), other.adminId()), 422),
            row("PUT /api/v1/training-groups/{groupId}", Caller.ADMIN, "group of another association", (own, other) -> "/api/v1/training-groups/" + other.groupId(),
                    (own, other) -> groupEditBody(own.entryLevelId(), own.adminId()), 404),
            row("PUT /api/v1/training-groups/{groupId}", Caller.ADMIN, "level of another association", (own, other) -> "/api/v1/training-groups/" + own.groupId(),
                    (own, other) -> groupEditBody(other.entryLevelId(), own.adminId()), 404),
            row("PUT /api/v1/training-groups/{groupId}", Caller.ADMIN, "coach of another association", (own, other) -> "/api/v1/training-groups/" + own.groupId(),
                    (own, other) -> groupEditBody(own.entryLevelId(), other.adminId()), 422),
            row("POST /api/v1/training-groups/{groupId}/archival", Caller.ADMIN, (own, other) -> "/api/v1/training-groups/" + other.groupId() + "/archival", (own, other) -> NO_BODY),
            // plans
            row("POST /api/v1/plans", Caller.ADMIN, "level of another association", (own, other) -> "/api/v1/plans",
                    (own, other) -> planBody(other.entryLevelId()), 404),
            row("PUT /api/v1/plans/{planId}", Caller.ADMIN, "plan of another association", (own, other) -> "/api/v1/plans/" + other.planId(),
                    (own, other) -> planBody(own.entryLevelId()), 404),
            row("PUT /api/v1/plans/{planId}", Caller.ADMIN, "level of another association", (own, other) -> "/api/v1/plans/" + own.planId(),
                    (own, other) -> planBody(other.entryLevelId()), 404),
            // join requests
            row("POST /api/v1/join-requests/{requestId}/approval", Caller.ADMIN, (own, other) -> "/api/v1/join-requests/" + other.approvableRequestId() + "/approval", (own, other) -> NO_BODY),
            row("POST /api/v1/join-requests/{requestId}/rejection", Caller.ADMIN, (own, other) -> "/api/v1/join-requests/" + other.rejectableRequestId() + "/rejection",
                    (own, other) -> "{\"reason\":\"no\"}"),
            // members
            row("PUT /api/v1/members/{memberId}/level", Caller.ADMIN_OR_COACH, "member of another association", (own, other) -> "/api/v1/members/" + other.spare().memberId() + "/level",
                    (own, other) -> "{\"levelId\":\"" + own.secondLevelId() + "\"}", 404),
            row("PUT /api/v1/members/{memberId}/level", Caller.ADMIN_OR_COACH, "level of another association", (own, other) -> "/api/v1/members/" + own.spare().memberId() + "/level",
                    (own, other) -> "{\"levelId\":\"" + other.secondLevelId() + "\"}", 404),
            row("PUT /api/v1/members/{memberId}/roles/{role}", Caller.ADMIN, (own, other) -> "/api/v1/members/" + other.spare().memberId() + "/roles/COACH", (own, other) -> NO_BODY),
            row("DELETE /api/v1/members/{memberId}/roles/{role}", Caller.ADMIN, (own, other) -> "/api/v1/members/" + other.spare().memberId() + "/roles/MEMBER", (own, other) -> NO_BODY),
            row("POST /api/v1/members/{memberId}/deactivation", Caller.ADMIN, (own, other) -> "/api/v1/members/" + other.spare().memberId() + "/deactivation", (own, other) -> NO_BODY),
            row("POST /api/v1/members/{memberId}/subscriptions", Caller.ADMIN, "member of another association", (own, other) -> "/api/v1/members/" + other.spare().memberId() + "/subscriptions",
                    (own, other) -> "{\"planId\":\"" + own.planId() + "\",\"startDate\":\"2027-06-01\"}", 404),
            row("POST /api/v1/members/{memberId}/subscriptions", Caller.ADMIN, "plan of another association", (own, other) -> "/api/v1/members/" + own.spare().memberId() + "/subscriptions",
                    (own, other) -> "{\"planId\":\"" + other.planId() + "\",\"startDate\":\"2027-06-01\"}", 404),
            // subscriptions and payments
            row("POST /api/v1/subscriptions/{subscriptionId}/overdue-marking", Caller.ADMIN, (own, other) -> "/api/v1/subscriptions/" + other.subscriptionId() + "/overdue-marking", (own, other) -> NO_BODY),
            row("POST /api/v1/subscriptions/{subscriptionId}/payments", Caller.ADMIN, (own, other) -> "/api/v1/subscriptions/" + other.subscriptionId() + "/payments",
                    (own, other) -> "{\"amountCents\":100,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}"),
            row("POST /api/v1/payments/{paymentId}/reversal", Caller.ADMIN, (own, other) -> "/api/v1/payments/" + other.paymentId() + "/reversal", (own, other) -> NO_BODY));

    /** Routes that take no id and show only the caller's own data: {@link #listsShowOnlyTheCallersOwnData} reads them. */
    private static final Set<String> LISTS = Set.of("GET /api/v1/sessions", "GET /api/v1/join-requests", "GET /api/v1/subscriptions",
            "GET /api/v1/subscriptions/export", "GET /api/v1/me/plan", "GET /api/v1/me/history");
    /** Routes with no id of any other resource to point at (they act on the caller's own association or account). */
    private static final Map<String, String> NO_FOREIGN_ID = Map.of(
            "POST /api/v1/levels", "creates in the caller's association; the name is the only input",
            "POST /api/v1/venues", "creates in the caller's association; the name, address and courts are the only input",
            "GET /api/v1/me", "reads the caller's own ids",
            "POST /api/v1/auth/logout-all", "acts on the caller's own account");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private TenantWorld a;
    private TenantWorld b;

    private void seed() throws Exception {
        if (a == null) {
            a = seedWorld();
            b = seedWorld();
        }
    }

    private static String groupBody(String levelId, String venueId, String coachId) {
        return "{\"name\":\"G\",\"acceptedLevelIds\":[\"" + levelId + "\"],\"venueId\":\"" + venueId + "\",\"schedule\":[{\"dayOfWeek\":\"FRIDAY\","
                + "\"startTime\":\"19:00\",\"durationMinutes\":60}],\"capacity\":10,\"coachId\":\"" + coachId + "\"}";
    }

    private static String groupEditBody(String levelId, String coachId) {
        return "{\"name\":\"G\",\"acceptedLevelIds\":[\"" + levelId + "\"],\"schedule\":[{\"dayOfWeek\":\"FRIDAY\","
                + "\"startTime\":\"19:00\",\"durationMinutes\":60}],\"capacity\":10,\"coachId\":\"" + coachId + "\"}";
    }

    private static String planBody(String levelId) {
        return "{\"name\":\"P\",\"type\":\"PACK\",\"credits\":5,\"allowedLevelIds\":[\"" + levelId + "\"],\"priceCents\":2000,\"validityDays\":30}";
    }

    private static String tokenOf(TenantWorld world, Caller caller) {
        return caller == Caller.MEMBER ? world.member().token() : world.adminToken();
    }

    /** A hash of every business row of the tenant: any change, in any column, changes it. */
    private String fingerprint(TenantWorld world) {
        String associationId = world.tenant().associationId();
        List<String> parts = new ArrayList<>();
        parts.add(jdbc.queryForObject("select coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from associations t where id = ?::uuid", String.class, associationId));
        for (String table : List.of("levels", "venues", "training_groups", "members", "join_requests", "plans", "subscriptions", "payments", "sessions",
                "bookings", "membership")) {
            parts.add(table + "=" + jdbc.queryForObject("select coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from " + table
                    + " t where association_id = ?::uuid", String.class, associationId));
        }
        // the credentials of the tenant's people (app_user has no association_id: it is reached through membership)
        String users = "select user_id from membership where association_id = ?::uuid";
        parts.add("app_user=" + jdbc.queryForObject("select coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from app_user t where id in (" + users + ")", String.class, associationId));
        parts.add("refresh_token=" + jdbc.queryForObject("select coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from refresh_token t where user_id in (" + users + ")", String.class, associationId));
        parts.add("email_link=" + jdbc.queryForObject("select coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from email_link t where user_id in (" + users + ")", String.class, associationId));
        return String.join(";", parts);
    }

    private static String normalised(MvcResult result) throws Exception {
        return result.getResponse().getStatus() + " " + result.getResponse().getContentAsString().replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"-\"");
    }

    @Test
    void everyAuthenticatedRouteHasARowHere() {
        // Arrange
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            String method = info.getMethodsCondition().getMethods().stream().map(RequestMethod::name).findFirst().orElse("ANY");
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                if (!PublicRoutes.permits(method.equals("ANY") ? "GET" : method, pattern.replaceAll("\\{[^}]+}", "x"))) {
                    routes.add(method + " " + pattern);
                }
            }
        }

        // Act
        Set<String> covered = Stream.concat(ROWS.stream().map(Row::route), Stream.concat(LISTS.stream(), NO_FOREIGN_ID.keySet().stream()))
                .collect(Collectors.toCollection(TreeSet::new));

        // Assert - a new authenticated route needs a row (or a stated reason it has no foreign id to point at)
        assertThat(covered).containsExactlyInAnyOrderElementsOf(routes);
    }

    @Test
    void anIdOfAnotherAssociationAnswersExactlyLikeAnIdThatExistsNowhereAndChangesNothing() throws Exception {
        // Arrange
        seed();
        TenantWorld ghost = TenantWorld.ghost();
        String before = fingerprint(a) + "|" + fingerprint(b);
        List<String> failures = new ArrayList<>();
        Mockito.clearInvocations(notifier, mailer);

        // Act
        for (Row row : ROWS) {
            String token = tokenOf(a, row.caller());
            MvcResult foreign = send(row.method(), row.path().apply(a, b), token, row.body().apply(a, b));
            MvcResult nowhere = send(row.method(), row.path().apply(a, ghost), token, row.body().apply(a, ghost));
            if (foreign.getResponse().getStatus() != row.foreignStatus()) {
                failures.add(row + ": status " + foreign.getResponse().getStatus() + ", expected " + row.foreignStatus() + " - " + body(foreign));
            }
            if (!normalised(foreign).equals(normalised(nowhere))) {
                failures.add(row + ": the foreign id answered differently from an id that exists nowhere: " + normalised(foreign) + " vs " + normalised(nowhere));
            }
        }
        String after = fingerprint(a) + "|" + fingerprint(b);

        // Assert
        assertThat(failures).isEmpty();
        assertThat(after).as("no row of either association changed, credentials included").isEqualTo(before);
        Mockito.verifyNoInteractions(notifier, mailer);
    }

    @Test
    void aPlainMemberGets403OnEveryAdministratorAndStaffRouteWithTheirOwnIds() throws Exception {
        // Arrange
        seed();
        String before = fingerprint(a);
        List<String> failures = new ArrayList<>();

        // Act
        for (Row row : ROWS) {
            if (row.caller() == Caller.MEMBER) {
                continue;
            }
            MvcResult result = send(row.method(), row.path().apply(a, a), a.member().token(), row.body().apply(a, a));
            if (result.getResponse().getStatus() != 403) {
                failures.add(row + ": a plain member got " + result.getResponse().getStatus() + " - " + body(result));
            }
        }
        for (String path : List.of("/api/v1/join-requests", "/api/v1/subscriptions?paymentStatus=PAID", "/api/v1/subscriptions/export?paymentStatus=PAID")) {
            MvcResult result = send(HttpMethod.GET, path, a.member().token());
            if (result.getResponse().getStatus() != 403) {
                failures.add("GET " + path + ": a plain member got " + result.getResponse().getStatus());
            }
        }
        for (Map.Entry<String, String> create : Map.of("/api/v1/levels", "{\"name\":\"X\"}", "/api/v1/venues", "{\"name\":\"X\",\"address\":\"Y\",\"courts\":1}").entrySet()) {
            MvcResult result = send(HttpMethod.POST, create.getKey(), a.member().token(), create.getValue());
            if (result.getResponse().getStatus() != 403) {
                failures.add("POST " + create.getKey() + ": a plain member got " + result.getResponse().getStatus());
            }
        }

        // Assert
        assertThat(failures).isEmpty();
        assertThat(fingerprint(a)).as("nothing changed").isEqualTo(before);
    }

    @Test
    void listsShowOnlyTheCallersOwnData() throws Exception {
        // Arrange
        seed();
        List<String> foreignIds = List.of(b.sessionId(), b.bookingId(), b.groupId(), b.subscriptionId(), b.paymentId(), b.approvableRequestId(),
                b.rejectableRequestId(), b.member().memberId(), b.spare().memberId(), b.adminId(), b.venueId(), b.planId());
        List<String> failures = new ArrayList<>();

        // Act
        Map<String, MvcResult> answers = Map.of(
                "GET /api/v1/sessions", send(HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14", a.member().token()),
                "GET /api/v1/join-requests", send(HttpMethod.GET, "/api/v1/join-requests", a.adminToken()),
                "GET /api/v1/subscriptions", send(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PENDING", a.adminToken()),
                "GET /api/v1/subscriptions/export", send(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PENDING", a.adminToken()),
                "GET /api/v1/me/plan", send(HttpMethod.GET, "/api/v1/me/plan", a.member().token()),
                "GET /api/v1/me/history", send(HttpMethod.GET, "/api/v1/me/history", a.member().token()));
        for (Map.Entry<String, MvcResult> answer : answers.entrySet()) {
            String text = answer.getValue().getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (answer.getValue().getResponse().getStatus() != 200) {
                failures.add(answer.getKey() + ": status " + answer.getValue().getResponse().getStatus());
            }
            for (String foreignId : foreignIds) {
                if (text.contains(foreignId)) {
                    failures.add(answer.getKey() + " shows an id of the other association: " + foreignId);
                }
            }
        }

        // Assert
        assertThat(failures).isEmpty();
        assertThat(body(answers.get("GET /api/v1/sessions"))).contains(a.sessionId());
        assertThat(body(answers.get("GET /api/v1/join-requests"))).contains(a.approvableRequestId()).contains(a.rejectableRequestId());
        assertThat(body(answers.get("GET /api/v1/subscriptions"))).contains(a.subscriptionId());
        assertThat(answers.get("GET /api/v1/subscriptions/export").getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(a.subscriptionId());
        assertThat(body(answers.get("GET /api/v1/me/plan"))).contains(a.subscriptionId());
    }

    @Test
    void aCoachIsNeitherAnAdministratorNorTheCoachOfAnySessionAndForeignIdsGetThemWhatNonExistentOnesDo() throws Exception {
        // Arrange - the second member is a coach, of no group
        seed();
        String coach = a.spare().token();
        TenantWorld ghost = TenantWorld.ghost();
        String before = fingerprint(a) + "|" + fingerprint(b);
        List<String> failures = new ArrayList<>();
        Mockito.clearInvocations(notifier, mailer);

        // Act
        for (Row row : ROWS) {
            if (row.caller() == Caller.MEMBER) {
                continue;
            }
            if (row.caller() != Caller.ADMIN_OR_COACH) {
                MvcResult own = send(row.method(), row.path().apply(a, a), coach, row.body().apply(a, a));
                if (own.getResponse().getStatus() != 403) {
                    failures.add(row + ": a coach with their own association's ids got " + own.getResponse().getStatus() + " - " + body(own));
                }
            }
            MvcResult foreign = send(row.method(), row.path().apply(a, b), coach, row.body().apply(a, b));
            MvcResult nowhere = send(row.method(), row.path().apply(a, ghost), coach, row.body().apply(a, ghost));
            if (!List.of(403, 404).contains(foreign.getResponse().getStatus())) {
                failures.add(row + ": a coach with foreign ids got " + foreign.getResponse().getStatus());
            }
            if (!normalised(foreign).equals(normalised(nowhere))) {
                failures.add(row + ": a coach's foreign id answered differently from an id that exists nowhere: " + normalised(foreign) + " vs " + normalised(nowhere));
            }
        }
        for (String path : List.of("/api/v1/join-requests", "/api/v1/subscriptions?paymentStatus=PAID", "/api/v1/subscriptions/export?paymentStatus=PAID")) {
            if (send(HttpMethod.GET, path, coach).getResponse().getStatus() != 403) {
                failures.add("GET " + path + ": a coach is not an administrator");
            }
        }

        // Assert
        assertThat(failures).isEmpty();
        assertThat(fingerprint(a) + "|" + fingerprint(b)).isEqualTo(before);
        Mockito.verifyNoInteractions(notifier, mailer);
    }

    @Test
    void sameAssociationAuthorisationHoldsOverHttpToo() throws Exception {
        // Arrange - a world of its own: this test books, so it shares no state with the others
        TenantWorld w = seedWorld();
        expect(201, HttpMethod.POST, "/api/v1/members/" + w.spare().memberId() + "/subscriptions", w.adminToken(),
                "{\"planId\":\"" + w.planId() + "\",\"startDate\":\"2026-10-12\"}");
        String sparesBooking = read(expect(201, HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/bookings", w.spare().token(), null), "$.bookingId");
        String before = fingerprint(w);

        // Act
        MvcResult memberCancelsAnothersBooking = send(HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/bookings/" + w.bookingId() + "/cancellation", w.spare().token());
        MvcResult otherCoachCancelsTheSession = send(HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/cancellation", w.spare().token(), "{\"reason\":\"x\"}");
        MvcResult otherCoachChangesCapacity = send(HttpMethod.PUT, "/api/v1/sessions/" + w.sessionId() + "/capacity", w.spare().token(), "{\"capacity\":3}");
        MvcResult memberMarksAttendance = send(HttpMethod.PUT, "/api/v1/sessions/" + w.sessionId() + "/attendance", w.member().token(),
                "{\"entries\":[{\"bookingId\":\"" + sparesBooking + "\",\"mark\":\"NO_SHOW\"}]}");

        // Assert - the second member is a coach, but not THIS session's coach: still no staff powers over it
        assertThat(memberCancelsAnothersBooking.getResponse().getStatus()).isEqualTo(403);
        assertThat(otherCoachCancelsTheSession.getResponse().getStatus()).isEqualTo(403);
        assertThat(otherCoachChangesCapacity.getResponse().getStatus()).isEqualTo(403);
        assertThat(memberMarksAttendance.getResponse().getStatus()).isEqualTo(403);
        assertThat(fingerprint(w)).isEqualTo(before);
        expect(200, HttpMethod.POST, "/api/v1/sessions/" + w.sessionId() + "/bookings/" + sparesBooking + "/cancellation", w.spare().token(), null);
    }
}
