package com.regivolley.api.integration;

import com.jayway.jsonpath.JsonPath;
import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.application.usecase.GenerateSessionsUseCase;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * The whole application over HTTP: the real filter chain, controllers, services, adapters and PostgreSQL (Flyway), with the clock
 * under the test's control (Monday 12 October 2026, 10:00 in Lisbon) and the mail adapter replaced by a mock that tells the test
 * which activation link was "sent". Everything a test sets up goes through the public API, the way a client would, except the
 * scheduler's session generation, which has no HTTP route. Every request comes from an address of its own, so the per-IP limits of
 * the public routes never cross between tests; every tenant has names of its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AbstractApiIntegrationTest.TestBeans.class)
public abstract class AbstractApiIntegrationTest extends AbstractPostgresIntegrationTest {

    protected static final String PASSWORD = "correct horse battery staple";
    protected static final Instant START = Instant.parse("2026-10-12T09:00:00Z");
    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        /** Runs background mail work on the calling thread, so tests need not wait. */
        @Bean
        @Primary
        BackgroundWork directBackgroundWork() {
            return (lane, work) -> work.run();
        }
    }

    /** A signed-in person of one association. */
    public record Person(String email, String token, String memberId) {
    }

    /** An association with its founder (administrator, also a coach) signed in. */
    public record Tenant(String shortName, String associationId, Person admin) {
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected GenerateSessionsUseCase generateSessions;
    @MockitoBean
    protected AccountLinkMailer mailer;
    /** The notifier is replaced too, so a test can prove that a refused call told nobody anything. */
    @MockitoBean
    protected Notifier notifier;

    @BeforeEach
    void resetClockAndMailer() {
        clock.set(START);
        Mockito.reset(mailer, notifier);
    }

    // ---- requests ----------------------------------------------------------------------------------------------

    protected MvcResult send(HttpMethod method, String path, String token, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).with(fromNewClient());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult send(HttpMethod method, String path, String token) throws Exception {
        return send(method, path, token, null);
    }

    /** Sends and requires the given status, so a set-up step that goes wrong fails where it happens. */
    protected MvcResult expect(int status, HttpMethod method, String path, String token, String body) throws Exception {
        MvcResult result = send(method, path, token, body);
        assertThat(result.getResponse().getStatus()).as("%s %s -> %s", method, path, result.getResponse().getContentAsString()).isEqualTo(status);
        return result;
    }

    protected static org.springframework.test.web.servlet.request.RequestPostProcessor fromNewClient() {
        int n = NEXT_CLIENT.getAndIncrement();
        String address = "10." + (n / 65000 % 250) + "." + (n / 250 % 250) + "." + (n % 250 + 1);
        return req -> {
            req.setRemoteAddr(address);
            return req;
        };
    }

    protected static <T> T read(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }

    protected static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /**
     * Seeds a tenant over HTTP with one of everything: three levels, a venue, a group coached by the administrator, a pack plan, a
     * member with a subscription, a part payment, a generated session with the member's booking, a second member (also a coach, of no group) and two pending
     * join requests.
     */
    protected TenantWorld seedWorld() throws Exception {
        Tenant tenant = registerTenant();
        String admin = tenant.admin().token();
        MvcResult levels = expect(201, HttpMethod.POST, "/api/v1/levels", admin, "{\"name\":\"Advanced\"}");
        String entry = read(levels, "$.entryLevelId");
        String second = read(levels, "$.levels[1].id");
        String third = read(levels, "$.levels[2].id");
        String venue = read(expect(201, HttpMethod.POST, "/api/v1/venues", admin, "{\"name\":\"Pavilion\",\"address\":\"Rua A 1\",\"courts\":2}"), "$.id");
        expect(200, HttpMethod.PUT, "/api/v1/members/" + tenant.admin().memberId() + "/roles/COACH", admin, null);
        String group = read(expect(201, HttpMethod.POST, "/api/v1/training-groups", admin, "{\"name\":\"Wednesday\",\"acceptedLevelIds\":[\""
                + entry + "\"],\"venueId\":\"" + venue + "\",\"schedule\":[{\"dayOfWeek\":\"WEDNESDAY\",\"startTime\":\"20:00\","
                + "\"durationMinutes\":90}],\"capacity\":12,\"coachId\":\"" + tenant.admin().memberId() + "\"}"), "$.id");
        String plan = read(expect(201, HttpMethod.POST, "/api/v1/plans", admin, "{\"name\":\"Ten sessions\",\"type\":\"PACK\",\"credits\":10,"
                + "\"allowedLevelIds\":[\"" + entry + "\"],\"priceCents\":4500,\"validityDays\":90}"), "$.id");
        Person member = joinAndActivate(tenant, "Rita Costa");
        Person spare = joinAndActivate(tenant, "Paulo Dias");
        // the second member is also a coach - of no group - so the tests can show that a coach is not the coach of just any session
        expect(200, HttpMethod.PUT, "/api/v1/members/" + spare.memberId() + "/roles/COACH", admin, null);
        String subscription = read(expect(201, HttpMethod.POST, "/api/v1/members/" + member.memberId() + "/subscriptions", admin,
                "{\"planId\":\"" + plan + "\",\"startDate\":\"2026-10-12\"}"), "$.id");
        String payment = read(expect(201, HttpMethod.POST, "/api/v1/subscriptions/" + subscription + "/payments", admin,
                "{\"amountCents\":1500,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}"), "$.payment.id");
        generateSessionsFor(tenant);
        String session = read(expect(200, HttpMethod.GET, "/api/v1/sessions?weekOf=2026-10-14", member.token(), null), "$[0].sessionId");
        String booking = read(expect(201, HttpMethod.POST, "/api/v1/sessions/" + session + "/bookings", member.token(), null), "$.bookingId");
        String approvable = pendingRequest(tenant, "Ines Lopes");
        String rejectable = pendingRequest(tenant, "Tiago Matos");
        return new TenantWorld(tenant, member, spare, entry, second, third, venue, group, plan, subscription, payment, session, booking,
                approvable, rejectable);
    }

    // ---- people ------------------------------------------------------------------------------------------------

    /** The link the (mock) mailer was last asked to send to this account address. */
    protected AccountLink sentLinkTo(String accountEmail, AccountLinkPurpose purpose) {
        List<AccountLink> sent = new ArrayList<>();
        Mockito.mockingDetails(mailer).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("send"))
                .filter(call -> call.getArgument(0).equals(EmailAddress.of(accountEmail)) && call.getArgument(1) == purpose)
                .forEach(call -> sent.add(call.getArgument(2)));
        assertThat(sent).as("a %s link was mailed to the account", purpose).isNotEmpty();
        return sent.get(sent.size() - 1);
    }

    protected static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return out.append('}').toString();
    }

    protected String registerBody(String shortName, String founderEmail) {
        return "{\"name\":\"Club " + shortName + "\",\"shortName\":\"" + shortName + "\",\"locality\":\"Lisbon\","
                + "\"contactEmail\":\"info@" + shortName + ".example\",\"levelNames\":[\"Beginner\",\"Intermediate\"],"
                + "\"founderName\":\"Founder " + shortName + "\",\"founderEmail\":\"" + founderEmail + "\",\"founderPhone\":\"912345678\","
                + "\"consentAccepted\":true,\"policyVersion\":\"2026-01\"}";
    }

    protected String joinBody(String name, String email) {
        return "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"phone\":\"912345679\",\"consentAccepted\":true,\"policyVersion\":\"2026-01\"}";
    }

    /** Activates the account behind the last link mailed to the address and signs it in; returns the access token. */
    protected String activateAndLogin(String email) throws Exception {
        AccountLink link = sentLinkTo(email, AccountLinkPurpose.ACTIVATION);
        expect(204, HttpMethod.POST, "/api/v1/auth/activate", null, json("token", link.token(), "password", PASSWORD));
        MvcResult login = expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", email, "password", PASSWORD));
        return read(login, "$.accessToken");
    }

    /** Registers an association over HTTP, activates its founder from the mailed link and signs them in. */
    protected Tenant registerTenant() throws Exception {
        String shortName = "club-" + unique();
        String email = "founder." + unique() + "@example.com";
        expect(201, HttpMethod.POST, "/api/v1/public/associations", null, registerBody(shortName, email));
        String token = activateAndLogin(email);
        MvcResult me = expect(200, HttpMethod.GET, "/api/v1/me", token, null);
        Person admin = new Person(email, token, read(me, "$.memberId"));
        return new Tenant(shortName, read(me, "$.associationId"), admin);
    }

    /** A visitor asks to join, the administrator approves, the new member activates and signs in. */
    protected Person joinAndActivate(Tenant tenant, String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody(name, email));
        MvcResult pending = expect(200, HttpMethod.GET, "/api/v1/join-requests", tenant.admin().token(), null);
        String requestId = idOfRequestFrom(pending, email);
        MvcResult approval = expect(200, HttpMethod.POST, "/api/v1/join-requests/" + requestId + "/approval", tenant.admin().token(), null);
        String memberId = read(approval, "$.memberId");
        return new Person(email, activateAndLogin(email), memberId);
    }

    private static String idOfRequestFrom(MvcResult pending, String email) throws Exception {
        List<String> ids = read(pending, "$[?(@.email == '" + email + "')].id");
        assertThat(ids).as("the pending request of %s", email).hasSize(1);
        return ids.get(0);
    }

    /** A visitor's join request that stays pending; returns its id. */
    protected String pendingRequest(Tenant tenant, String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody(name, email));
        MvcResult pending = expect(200, HttpMethod.GET, "/api/v1/join-requests", tenant.admin().token(), null);
        return idOfRequestFrom(pending, email);
    }

    /** Runs the scheduler's generation for the tenant (it has no HTTP route, threat model: never exposed). */
    protected void generateSessionsFor(Tenant tenant) {
        generateSessions.execute(new GenerateSessionsCommand(AssociationId.of(UUID.fromString(tenant.associationId()))));
    }
}
