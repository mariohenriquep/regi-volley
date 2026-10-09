package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.usecase.UseCase;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Threat model 10.1 and 10.9 for every authenticated endpoint of the controllers: no token is a 401, and what a use case refuses
 * comes out as the documented status with the one error body - {@code NotAllowedException} 403, a missing (or another association's)
 * aggregate 404, a conflict 409, any other rule 422. The use cases are mocks, so this proves the controllers add nothing between the
 * use case and the advice (a controller that caught an exception, or a route that bypasses the advice, fails here).
 */
class EndpointErrorMappingWebTest extends AbstractSecuredWebTest {

    private static final UUID ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    /** The routes that are not in this table, and why. */
    private static final Set<String> NOT_COVERED = Set.of(
            "GET /api/v1/me",                    // reads no use case: the security layer's own probe, tested in TokenAuthenticationTest
            "POST /api/v1/auth/logout-all");     // a credential use case with its own tests in AuthEndpointsWebTest

    private record Endpoint(String route, HttpMethod method, String path, String body, Function<EndpointErrorMappingWebTest, UseCase<?, ?>> useCase) {
        @Override
        public String toString() {
            return route;
        }
    }

    private static Endpoint endpoint(String route, String path, String body, Function<EndpointErrorMappingWebTest, UseCase<?, ?>> useCase) {
        String[] parts = route.split(" ", 2);
        return new Endpoint(route, HttpMethod.valueOf(parts[0]), path, body, useCase);
    }

    private static final String LEVEL = "{\"levelId\":\"" + ID + "\"}";
    private static final String NAME = "{\"name\":\"Name\"}";
    private static final String VENUE = "{\"name\":\"Pavilion\",\"address\":\"Rua A\",\"courts\":2}";
    private static final String SCHEDULE = "\"schedule\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"20:00\",\"durationMinutes\":90}]";
    private static final String GROUP = "{\"name\":\"G\",\"acceptedLevelIds\":[\"" + ID + "\"],\"venueId\":\"" + ID + "\"," + SCHEDULE
            + ",\"capacity\":12,\"coachId\":\"" + ID + "\"}";
    private static final String GROUP_EDIT = "{\"name\":\"G\",\"acceptedLevelIds\":[\"" + ID + "\"]," + SCHEDULE + ",\"capacity\":12,\"coachId\":\"" + ID + "\"}";
    private static final String PLAN = "{\"name\":\"P\",\"type\":\"MONTHLY_UNLIMITED\",\"priceCents\":3000}";

    static Stream<Endpoint> endpoints() {
        return Stream.of(
                endpoint("GET /api/v1/me/plan", "/api/v1/me/plan", null, t -> t.myPlanUseCase),
                endpoint("GET /api/v1/me/history", "/api/v1/me/history", null, t -> t.memberHistoryUseCase),
                endpoint("GET /api/v1/sessions", "/api/v1/sessions", null, t -> t.listBookableSessionsUseCase),
                endpoint("POST /api/v1/sessions/{sessionId}/bookings", "/api/v1/sessions/" + ID + "/bookings", null, t -> t.bookSessionUseCase),
                endpoint("POST /api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation", "/api/v1/sessions/" + ID + "/bookings/" + OTHER_ID + "/cancellation", null, t -> t.cancelBookingUseCase),
                endpoint("POST /api/v1/sessions/{sessionId}/cancellation", "/api/v1/sessions/" + ID + "/cancellation", "{\"reason\":\"Closed\"}", t -> t.cancelSessionUseCase),
                endpoint("PUT /api/v1/sessions/{sessionId}/capacity", "/api/v1/sessions/" + ID + "/capacity", "{\"capacity\":10}", t -> t.changeSessionCapacityUseCase),
                endpoint("GET /api/v1/sessions/{sessionId}/roster", "/api/v1/sessions/" + ID + "/roster", null, t -> t.getSessionRosterUseCase),
                endpoint("PUT /api/v1/sessions/{sessionId}/attendance", "/api/v1/sessions/" + ID + "/attendance",
                        "{\"entries\":[{\"bookingId\":\"" + OTHER_ID + "\",\"mark\":\"ATTENDED\"}]}", t -> t.markAttendanceUseCase),
                endpoint("POST /api/v1/levels", "/api/v1/levels", NAME, t -> t.addLevelUseCase),
                endpoint("PUT /api/v1/levels/{levelId}", "/api/v1/levels/" + ID, NAME, t -> t.renameLevelUseCase),
                endpoint("PUT /api/v1/levels/order", "/api/v1/levels/order", "{\"levelIds\":[\"" + ID + "\"]}", t -> t.reorderLevelsUseCase),
                endpoint("PUT /api/v1/levels/entry-level", "/api/v1/levels/entry-level", LEVEL, t -> t.changeEntryLevelUseCase),
                endpoint("POST /api/v1/venues", "/api/v1/venues", VENUE, t -> t.createVenueUseCase),
                endpoint("PUT /api/v1/venues/{venueId}", "/api/v1/venues/" + ID, VENUE, t -> t.editVenueUseCase),
                endpoint("DELETE /api/v1/venues/{venueId}", "/api/v1/venues/" + ID, null, t -> t.deleteVenueUseCase),
                endpoint("POST /api/v1/training-groups", "/api/v1/training-groups", GROUP, t -> t.createTrainingGroupUseCase),
                endpoint("PUT /api/v1/training-groups/{groupId}", "/api/v1/training-groups/" + ID, GROUP_EDIT, t -> t.editTrainingGroupUseCase),
                endpoint("POST /api/v1/training-groups/{groupId}/archival", "/api/v1/training-groups/" + ID + "/archival", null, t -> t.archiveTrainingGroupUseCase),
                endpoint("POST /api/v1/plans", "/api/v1/plans", PLAN, t -> t.createPlanUseCase),
                endpoint("PUT /api/v1/plans/{planId}", "/api/v1/plans/" + ID, PLAN, t -> t.editPlanUseCase),
                endpoint("GET /api/v1/join-requests", "/api/v1/join-requests", null, t -> t.listPendingJoinRequestsUseCase),
                endpoint("POST /api/v1/join-requests/{requestId}/approval", "/api/v1/join-requests/" + ID + "/approval", null, t -> t.approveJoinRequestUseCase),
                endpoint("POST /api/v1/join-requests/{requestId}/rejection", "/api/v1/join-requests/" + ID + "/rejection", "{\"reason\":\"No room\"}", t -> t.rejectJoinRequestUseCase),
                endpoint("PUT /api/v1/members/{memberId}/level", "/api/v1/members/" + ID + "/level", LEVEL, t -> t.changeMemberLevelUseCase),
                endpoint("PUT /api/v1/members/{memberId}/roles/{role}", "/api/v1/members/" + ID + "/roles/COACH", null, t -> t.grantRoleUseCase),
                endpoint("DELETE /api/v1/members/{memberId}/roles/{role}", "/api/v1/members/" + ID + "/roles/COACH", null, t -> t.revokeRoleUseCase),
                endpoint("POST /api/v1/members/{memberId}/deactivation", "/api/v1/members/" + ID + "/deactivation", null, t -> t.deactivateMemberUseCase),
                endpoint("POST /api/v1/members/{memberId}/activation-links", "/api/v1/members/" + ID + "/activation-links", null, t -> t.resendActivationLinkUseCase),
                endpoint("POST /api/v1/members/{memberId}/subscriptions", "/api/v1/members/" + ID + "/subscriptions", "{\"planId\":\"" + OTHER_ID + "\"}", t -> t.assignPlanUseCase),
                endpoint("GET /api/v1/subscriptions", "/api/v1/subscriptions?paymentStatus=OVERDUE", null, t -> t.listSubscriptionsByPaymentStatusUseCase),
                endpoint("GET /api/v1/subscriptions/export", "/api/v1/subscriptions/export?paymentStatus=OVERDUE", null, t -> t.listSubscriptionsByPaymentStatusUseCase),
                endpoint("POST /api/v1/subscriptions/{subscriptionId}/overdue-marking", "/api/v1/subscriptions/" + ID + "/overdue-marking", null, t -> t.markSubscriptionOverdueUseCase),
                endpoint("POST /api/v1/subscriptions/{subscriptionId}/payments", "/api/v1/subscriptions/" + ID + "/payments",
                        "{\"amountCents\":1000,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}", t -> t.recordPaymentUseCase),
                endpoint("POST /api/v1/payments/{paymentId}/reversal", "/api/v1/payments/" + ID + "/reversal", null, t -> t.reversePaymentUseCase));
    }

    private MockHttpServletRequestBuilder build(Endpoint endpoint, boolean withToken) {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.path());
        if (withToken) {
            builder.header("Authorization", bearer());
        }
        return endpoint.body() == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void failWith(Endpoint endpoint, RuntimeException failure) {
        UseCase raw = endpoint.useCase().apply(this);
        doThrow(failure).when(raw).execute(any());
    }

    @Test
    void theTableCoversEveryAuthenticatedRouteOfTheInventory() {
        // Arrange
        Set<String> inventory = RouteInventoryTest.CLASSIFICATION.entrySet().stream()
                .filter(entry -> entry.getValue() == RouteInventoryTest.Access.AUTHENTICATED)
                .map(java.util.Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));

        // Act
        Set<String> covered = endpoints().map(Endpoint::route).collect(Collectors.toCollection(TreeSet::new));
        covered.addAll(NOT_COVERED);

        // Assert - a new authenticated route must be added to this table (or to NOT_COVERED with a reason)
        assertThat(covered).containsExactlyInAnyOrderElementsOf(inventory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void withoutATokenIs401(Endpoint endpoint) throws Exception {
        // Arrange
        // (the endpoint)

        // Act
        var result = mockMvc.perform(build(endpoint, false));

        // Assert
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void notAllowedIs403(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new NotAllowedException("do this"));

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_ALLOWED")).andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anAggregateThatIsMissingOrBelongsToAnotherAssociationIs404WithoutEchoingTheId(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new SessionNotFoundException(SessionId.of(OTHER_ID)));

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(OTHER_ID.toString()))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void aConflictIs409(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new LastAdministratorException());

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void aLostRaceAfterTheRetriesIs409(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new SessionModifiedConcurrentlyException(SessionId.of(OTHER_ID)));

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anyOtherBusinessRuleIs422WithItsOwnMessage(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new CoachCannotBookOwnSessionException());

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value(new CoachCannotBookOwnSessionException().getMessage()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anUnexpectedFailureIsAGeneric500WithNoExceptionText(Endpoint endpoint) throws Exception {
        // Arrange
        failWith(endpoint, new IllegalStateException("could not execute SQL for ana.silva@example.com"));

        // Act
        var result = mockMvc.perform(build(endpoint, true));

        // Assert
        result.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("ana.silva"))));
    }

    @Test
    void theHelpersOfTheTableAreSane() {
        // Arrange
        List<Endpoint> all = endpoints().toList();

        // Act
        long distinct = all.stream().map(Endpoint::route).distinct().count();

        // Assert
        assertThat(distinct).isEqualTo(all.size());
        assertThat(new DuplicateBookingException().getMessage()).isNotBlank();
        assertThat(new BookingNotFoundException(BookingId.generate())).isNotNull();
    }
}
