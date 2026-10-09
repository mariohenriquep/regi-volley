package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route inventory (threat model section 10.1): every route the application maps is classified, here, as public or authenticated.
 * A controller that adds a route without adding a row fails the build; so does a row that contradicts the filter chain.
 * Add a row to {@link #CLASSIFICATION} in the same change as the controller.
 */
class RouteInventoryTest extends AbstractSecuredWebTest {

    enum Access { PUBLIC, AUTHENTICATED }

    /** "METHOD pattern" (ANY when the mapping names no method) to who may call it. */
    static final Map<String, Access> CLASSIFICATION = Map.ofEntries(
            // identity and credentials (26a, 26b)
            Map.entry("GET /api/v1/me", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/auth/login", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/refresh", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/logout", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/logout-all", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/auth/activate", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/password-reset-requests", Access.PUBLIC),
            Map.entry("POST /api/v1/auth/password-resets", Access.PUBLIC),
            Map.entry("ANY /error", Access.PUBLIC),
            // the visitor (26c)
            Map.entry("GET /api/v1/public/associations/{shortName}", Access.PUBLIC),
            Map.entry("POST /api/v1/public/associations", Access.PUBLIC),
            Map.entry("POST /api/v1/public/associations/{shortName}/join-requests", Access.PUBLIC),
            // the member
            Map.entry("GET /api/v1/me/plan", Access.AUTHENTICATED),
            Map.entry("GET /api/v1/me/history", Access.AUTHENTICATED),
            Map.entry("GET /api/v1/sessions", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/sessions/{sessionId}/bookings", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/sessions/{sessionId}/bookings/{bookingId}/cancellation", Access.AUTHENTICATED),
            // the staff of a session
            Map.entry("POST /api/v1/sessions/{sessionId}/cancellation", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/sessions/{sessionId}/capacity", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/sessions/{sessionId}/attendance", Access.AUTHENTICATED),
            // the administrator
            Map.entry("POST /api/v1/levels", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/levels/{levelId}", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/levels/order", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/levels/entry-level", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/venues", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/venues/{venueId}", Access.AUTHENTICATED),
            Map.entry("DELETE /api/v1/venues/{venueId}", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/training-groups", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/training-groups/{groupId}", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/training-groups/{groupId}/archival", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/plans", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/plans/{planId}", Access.AUTHENTICATED),
            Map.entry("GET /api/v1/join-requests", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/join-requests/{requestId}/approval", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/join-requests/{requestId}/rejection", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/members/{memberId}/level", Access.AUTHENTICATED),
            Map.entry("PUT /api/v1/members/{memberId}/roles/{role}", Access.AUTHENTICATED),
            Map.entry("DELETE /api/v1/members/{memberId}/roles/{role}", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/members/{memberId}/deactivation", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/members/{memberId}/subscriptions", Access.AUTHENTICATED),
            Map.entry("GET /api/v1/subscriptions", Access.AUTHENTICATED),
            Map.entry("GET /api/v1/subscriptions/export", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/subscriptions/{subscriptionId}/overdue-marking", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/subscriptions/{subscriptionId}/payments", Access.AUTHENTICATED),
            Map.entry("POST /api/v1/payments/{paymentId}/reversal", Access.AUTHENTICATED));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private Set<String> routes;

    @BeforeEach
    void scanRoutes() {
        Set<String> found = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> methods = info.getMethodsCondition().getMethods().stream().map(RequestMethod::name)
                    .collect(Collectors.toCollection(TreeSet::new));
            String method = methods.isEmpty() ? "ANY" : String.join(",", methods);
            for (String pattern : info.getPathPatternsCondition().getPatternValues()) {
                found.add(method + " " + pattern);
            }
        }
        routes = found;
    }

    /** The routes without a classification. */
    static Set<String> unclassified(Set<String> routes, Map<String, Access> classification) {
        return routes.stream().filter(route -> !classification.containsKey(route)).collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void theInventoryActuallySeesTheApplicationsRoutes() {
        // Arrange
        Set<String> expected = Set.of("GET /api/v1/me", "POST /api/v1/auth/login", "POST /api/v1/auth/logout-all", "ANY /error");

        // Act
        Set<String> found = routes;

        // Assert - guards the scan itself: an empty inventory would make every check below pass vacuously
        assertThat(found).containsAll(expected);
    }

    @Test
    void anUnclassifiedRouteIsDetected() {
        // Arrange
        Set<String> fakeRoutes = Set.of("GET /api/v1/me", "DELETE /api/v1/members/{id}");

        // Act
        Set<String> missing = unclassified(fakeRoutes, CLASSIFICATION);

        // Assert
        assertThat(missing).containsExactly("DELETE /api/v1/members/{id}");
    }

    @Test
    void everyMappedRouteIsClassified() {
        // Arrange
        Map<String, Access> classification = CLASSIFICATION;

        // Act
        Set<String> missing = unclassified(routes, classification);

        // Assert
        assertThat(missing).as("add these to RouteInventoryTest.CLASSIFICATION as PUBLIC or AUTHENTICATED").isEmpty();
    }

    @Test
    void theClassificationNamesNoRouteThatDoesNotExist() {
        // Arrange
        Set<String> declared = CLASSIFICATION.keySet();

        // Act
        Set<String> stale = declared.stream().filter(route -> !routes.contains(route)).collect(Collectors.toCollection(TreeSet::new));

        // Assert
        assertThat(stale).isEmpty();
    }

    @Test
    void theFilterChainAgreesWithTheClassificationOfEveryRoute() throws Exception {
        // Arrange
        Set<String> disagreements = new TreeSet<>();

        // Act
        for (String route : routes) {
            String[] parts = route.split(" ", 2);
            String method = parts[0].equals("ANY") ? "GET" : parts[0].split(",")[0];
            String path = parts[1].replaceAll("\\{[^}]+}", "x");
            Access declared = CLASSIFICATION.get(route);
            if (PublicRoutes.permits(method, path) != (declared == Access.PUBLIC)) {
                disagreements.add(route);
            }
            if (declared == Access.AUTHENTICATED) {
                ResultActions anonymous = mockMvc.perform(request(HttpMethod.valueOf(method), path));
                anonymous.andExpect(status().isUnauthorized());
            }
        }

        // Assert
        assertThat(disagreements).isEmpty();
    }

    @Test
    void theCredentialAndPublicPageRoutesOfTheThreatModelAreTheOnlyOnesThatNeedNoToken() {
        // Arrange
        Set<String> publicRoutes = PublicRoutes.ALL.stream().map(route -> (route.method() == null ? "ANY" : route.method().name())
                + " " + route.pattern()).collect(Collectors.toSet());

        // Act
        Set<String> expected = Set.of(
                "POST /api/v1/public/associations",
                "GET /api/v1/public/associations/{shortName}",
                "POST /api/v1/public/associations/{shortName}/join-requests",
                "POST /api/v1/auth/login",
                "POST /api/v1/auth/refresh",
                "POST /api/v1/auth/logout",
                "POST /api/v1/auth/activate",
                "POST /api/v1/auth/password-reset-requests",
                "POST /api/v1/auth/password-resets",
                "ANY /error");

        // Assert
        assertThat(publicRoutes).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void anythingUnlistedIsDeniedByDefaultForAnonymousCallers() {
        // Arrange
        String members = "/api/v1/members";

        // Act
        boolean listed = PublicRoutes.permits("GET", members);

        // Assert
        assertThat(listed).isFalse();
        assertThat(PublicRoutes.permits("DELETE", "/api/v1/public/associations/club")).isFalse();
        assertThat(PublicRoutes.permits("GET", "/api/v1/public/associations/club/join-requests")).isFalse();
        assertThat(PublicRoutes.permits("GET", "/actuator/health")).isFalse();
    }
}
