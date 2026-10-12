package com.regivolley.api.infrastructure.devseed;

import com.jayway.jsonpath.JsonPath;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Issue #59: with profile {@code dev} the application starts with a ready-to-use association, and the seed runs again without
 * changing anything. The whole stack is real (Flyway, PostgreSQL, the filter chain); only the clock is fixed to a Monday morning
 * in Lisbon and the mail host is left unset, so the logging mail adapters stay.
 */
@SpringBootTest(properties = {"regi-volley.mail.host=", "regi-volley.dev-seed.enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Import(DevDataSeedIntegrationTest.FixedClock.class)
class DevDataSeedIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "dev-password-volley";
    private static final String ADMIN_EMAIL = "admin@volei-lisboa.test";
    private static final String COACH_EMAIL = "coach@volei-lisboa.test";
    private static final Instant MONDAY_MORNING = Instant.parse("2026-10-12T09:00:00Z");

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock fixedClock() {
            return new MutableClock(MONDAY_MORNING);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DevDataInitializer initializer;
    @Autowired
    private ApplicationContext context;

    @Test
    void theInitializerIsActiveUnderDev() {
        // Arrange / Act
        boolean dev = context.getEnvironment().acceptsProfiles(org.springframework.core.env.Profiles.of("dev"));

        // Assert
        assertThat(dev).isTrue();
        assertThat(context.getBeansOfType(DevDataInitializer.class)).hasSize(1);
    }

    @Test
    void theSeedCreatesTheAssociationWithItsLevelsVenueGroupPlanAndSessions() {
        // Arrange
        String where = " WHERE association_id = (SELECT id FROM associations WHERE short_name = 'volei-lisboa')";

        // Act
        Integer associations = jdbc.queryForObject("SELECT count(*) FROM associations WHERE short_name = 'volei-lisboa'", Integer.class);
        Integer levels = jdbc.queryForObject("SELECT count(*) FROM levels" + where, Integer.class);
        Integer venues = jdbc.queryForObject("SELECT count(*) FROM venues" + where, Integer.class);
        Integer groups = jdbc.queryForObject("SELECT count(*) FROM training_groups" + where, Integer.class);
        Integer plans = jdbc.queryForObject("SELECT count(*) FROM plans" + where, Integer.class);
        Integer sessions = jdbc.queryForObject("SELECT count(*) FROM sessions" + where, Integer.class);
        Integer members = jdbc.queryForObject("SELECT count(*) FROM members" + where, Integer.class);

        // Assert
        assertThat(associations).isEqualTo(1);
        assertThat(levels).isEqualTo(3);
        assertThat(venues).isEqualTo(1);
        assertThat(groups).isEqualTo(1);
        assertThat(plans).isEqualTo(1);
        assertThat(sessions).isGreaterThan(0);
        assertThat(members).isEqualTo(2);
    }

    @Test
    void theAdminSignsInIsNotTheCoachAndBooksAGeneratedSession() throws Exception {
        // Arrange
        String adminToken = login(ADMIN_EMAIL);
        String coachToken = login(COACH_EMAIL);
        String adminMember = JsonPath.read(body(authenticatedGet("/api/v1/me", adminToken)), "$.memberId");
        String coachMember = JsonPath.read(body(authenticatedGet("/api/v1/me", coachToken)), "$.memberId");
        String groupCoach = jdbc.queryForObject("SELECT coach_id::text FROM training_groups WHERE association_id = "
                + "(SELECT id FROM associations WHERE short_name = 'volei-lisboa')", String.class);
        List<String> sessionIds = JsonPath.read(body(authenticatedGet("/api/v1/sessions?weekOf=2026-10-12", adminToken)), "$[*].sessionId");

        // Act
        MvcResult booking = mockMvc.perform(post("/api/v1/sessions/" + sessionIds.get(0) + "/bookings")
                .header("Authorization", "Bearer " + adminToken)).andReturn();

        // Assert
        assertThat(groupCoach).isEqualTo(coachMember).isNotEqualTo(adminMember);
        assertThat(sessionIds).isNotEmpty();
        assertThat(booking.getResponse().getStatus()).as(body(booking)).isEqualTo(201);
        assertThat((String) JsonPath.read(body(booking), "$.status")).isEqualTo("CONFIRMED");
    }

    @Test
    void theAdminsPackIsPaidAndTheCoachCanSignInToo() throws Exception {
        // Arrange
        String adminToken = login(ADMIN_EMAIL);

        // Act
        MvcResult plan = authenticatedGet("/api/v1/me/plan", adminToken);

        // Assert
        assertThat((List<String>) JsonPath.read(body(plan), "$.subscriptions[*].paymentStatus")).containsExactly("PAID");
        assertThat(login(COACH_EMAIL)).isNotBlank();
    }

    @Test
    void runningTheSeedAgainChangesNothing() {
        // Arrange
        Integer associationsBefore = jdbc.queryForObject("SELECT count(*) FROM associations WHERE short_name = 'volei-lisboa'", Integer.class);
        Integer sessionsBefore = jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class);
        Integer membersBefore = jdbc.queryForObject("SELECT count(*) FROM members", Integer.class);
        Integer usersBefore = jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class);

        // Act
        initializer.run(null);

        // Assert
        assertThat(jdbc.queryForObject("SELECT count(*) FROM associations WHERE short_name = 'volei-lisboa'", Integer.class))
                .isEqualTo(associationsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class)).isEqualTo(sessionsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Integer.class)).isEqualTo(membersBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(usersBefore);
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).as("login of the seeded account: %s", body(result)).isEqualTo(200);
        return JsonPath.read(body(result), "$.accessToken");
    }

    private MvcResult authenticatedGet(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
        assertThat(result.getResponse().getStatus()).as("%s -> %s", path, body(result)).isEqualTo(200);
        return result;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }
}
