package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AccessTokenIssuer;

import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.RefreshTokenPolicy;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.application.usecase.ApproveJoinRequestUseCase;
import com.regivolley.api.application.usecase.RegisterAssociationUseCase;
import com.regivolley.api.application.usecase.SubmitJoinRequestUseCase;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.application.result.JoinRequestApproval;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.testsupport.MutableClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The credential flows end to end - real filter chain, controllers, services, adapters and PostgreSQL (Flyway) - with the clock
 * under the test's control and the mail adapter replaced by a mock that tells the test what link was "sent". Threat model
 * section 10: login and refresh behaviour, immediate revocation, uniform answers, single-use links, throttling, hashed secrets
 * and clean logs. Every test registers its own association and uses its own client addresses, so tests do not disturb each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CredentialsFlowIntegrationTest.TestBeans.class)
class CredentialsFlowIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String COOKIE = "__Secure-rt";
    private static final String PASSWORD = "correct horse battery staple";
    private static final Instant START = Instant.parse("2026-10-12T09:00:00Z");
    private static final AtomicInteger NEXT = new AtomicInteger(1);
    private static final Pattern REQUEST_ID = Pattern.compile("\"requestId\":\"[^\"]*\"");

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(START);
        }

        /** Runs the background mail work on the calling thread, so tests need not wait. */
        @Bean
        @Primary
        BackgroundWork directBackgroundWork() {
            return (lane, work) -> work.run();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MutableClock clock;
    @Autowired
    private RegisterAssociationUseCase register;
    @Autowired
    private SubmitJoinRequestUseCase submitJoinRequest;
    @Autowired
    private ApproveJoinRequestUseCase approveJoinRequest;
    @Autowired
    private MemberRepository members;
    @Autowired
    private AccessTokenIssuer issuer;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private AccountLinkMailer mailer;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setUp() {
        clock.set(START);
        Mockito.reset(mailer);
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void detach() {
        root.detachAppender(logs);
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private record Founder(AssociationId associationId, MemberId memberId, String email) {
        Actor actor() {
            return new Actor(associationId, memberId);
        }
    }

    private static RequestPostProcessor fromNewClient() {
        int n = NEXT.getAndIncrement();
        String address = "10." + (n / 65000 % 250) + "." + (n / 250 % 250) + "." + (n % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private Founder registerFounder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "founder." + suffix + "@example.com";
        AssociationRegistered registered = register.execute(new RegisterAssociationCommand("Club " + suffix, "club-" + suffix, null,
                "Lisbon", "info@club-" + suffix + ".example", List.of("Beginner"), "Founder Name", email, "912345678", true, "2026-01"));
        return new Founder(registered.associationId(), registered.founderId(), email);
    }

    /** The link the mailer was last asked to send to this account address (the address the account was created with). */
    private AccountLink sentLinkTo(String accountEmail, AccountLinkPurpose purpose) {
        List<AccountLink> sent = new ArrayList<>();
        Mockito.mockingDetails(mailer).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("send"))
                .filter(call -> call.getArgument(0).equals(EmailAddress.of(accountEmail)) && call.getArgument(1) == purpose)
                .forEach(call -> sent.add(call.getArgument(2)));
        assertThat(sent).as("a %s link was mailed to the account", purpose).isNotEmpty();
        return sent.get(sent.size() - 1);
    }

    private MvcResult postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(fromNewClient()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            out.append(i == 0 ? "" : ",").append('"').append(pairs[i]).append("\":\"").append(pairs[i + 1]).append('"');
        }
        return out.append('}').toString();
    }

    private MvcResult activate(AccountLink link, String password) throws Exception {
        return postJson("/api/v1/auth/activate", json("token", link.token(), "password", password));
    }

    private MvcResult login(String email, String password) throws Exception {
        return postJson("/api/v1/auth/login", json("email", email, "password", password));
    }

    private Founder activatedFounder() throws Exception {
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);
        assertThat(activate(link, PASSWORD).getResponse().getStatus()).isEqualTo(204);
        return founder;
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie(COOKIE, refreshToken)).content("{}")).andReturn();
    }

    private MvcResult me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me").with(fromNewClient()).header("Authorization", "Bearer " + accessToken)).andReturn();
    }

    private static String accessTokenOf(MvcResult result) throws Exception {
        Matcher matcher = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String refreshTokenOf(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).as("a refresh cookie was set").isNotNull();
        Matcher matcher = Pattern.compile(COOKIE + "=([^;]*);").matcher(header);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String logText() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : logs.list) {
            lines.add(event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                lines.add(event.getThrowableProxy().getMessage());
            }
        }
        return String.join("\n", lines);
    }

    /** Status, headers (minus the ones that must differ) and body (minus the request id) of a response, comparable across requests. */
    private static Map<String, Object> shape(MvcResult result) throws Exception {
        Map<String, Object> shape = new TreeMap<>();
        shape.put("status", result.getResponse().getStatus());
        shape.put("body", REQUEST_ID.matcher(result.getResponse().getContentAsString()).replaceAll("\"requestId\":\"-\""));
        for (String name : result.getResponse().getHeaderNames()) {
            if (!name.equalsIgnoreCase("X-Request-Id") && !name.equalsIgnoreCase("Date")) {
                shape.put("header:" + name.toLowerCase(), result.getResponse().getHeaders(name));
            }
        }
        return shape;
    }

    // ---- the journey -------------------------------------------------------------------------------------------

    @Test
    void aFounderActivatesFromTheEmailedLinkLogsInAndActsAsTheirMember() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);

        // Act
        MvcResult activated = activate(link, PASSWORD);
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        MvcResult whoAmI = me(accessTokenOf(loggedIn));

        // Assert
        assertThat(activated.getResponse().getStatus()).isEqualTo(204);
        assertThat(loggedIn.getResponse().getStatus()).isEqualTo(200);
        assertThat(whoAmI.getResponse().getStatus()).isEqualTo(200);
        assertThat(whoAmI.getResponse().getContentAsString()).contains(founder.memberId().toString()).contains(founder.associationId().toString());
    }

    @Test
    void anApprovedMemberGetsTheSameJourney() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        String email = "rita." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JoinRequestSubmitted submitted = submitJoinRequest.execute(new SubmitJoinRequestCommand(
                shortNameOf(founder), "Rita Costa", email, "912345678", true, "2026-01"));
        JoinRequestApproval approval = approveJoinRequest.execute(new ApproveJoinRequestCommand(founder.actor(), submitted.requestId()));
        AccountLink link = sentLinkTo(email, AccountLinkPurpose.ACTIVATION);

        // Act
        MvcResult loginBefore = login(email, PASSWORD);
        activate(link, PASSWORD);
        MvcResult loginAfter = login(email, PASSWORD);

        // Assert
        assertThat(loginBefore.getResponse().getStatus()).isEqualTo(401);
        assertThat(loginAfter.getResponse().getStatus()).isEqualTo(200);
        assertThat(me(accessTokenOf(loginAfter)).getResponse().getContentAsString()).contains(approval.member().id().toString());
    }

    private String shortNameOf(Founder founder) {
        return jdbc.queryForObject("select short_name from associations where id = ?", String.class, founder.associationId().value());
    }

    @Test
    void nobodyCanSetAPasswordWithoutTheMailedToken() throws Exception {
        // Arrange - pre-hijacking: the attacker knows the victim's email and tries to set a password first
        Founder victim = registerFounder();

        // Act
        MvcResult guess = postJson("/api/v1/auth/activate", json("token", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "password", PASSWORD));
        MvcResult empty = postJson("/api/v1/auth/activate", json("token", "x", "password", PASSWORD));
        MvcResult login = login(victim.email(), PASSWORD);

        // Assert
        assertThat(guess.getResponse().getStatus()).isEqualTo(400);
        assertThat(empty.getResponse().getStatus()).isEqualTo(400);
        assertThat(login.getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ? and password_hash is not null", Integer.class,
                victim.email())).isZero();
    }

    @Test
    void aLinkWorksOnceAndOnlyUntilItExpires() throws Exception {
        // Arrange
        Founder first = registerFounder();
        AccountLink once = sentLinkTo(first.email(), AccountLinkPurpose.ACTIVATION);
        Founder second = registerFounder();
        AccountLink late = sentLinkTo(second.email(), AccountLinkPurpose.ACTIVATION);

        // Act
        int firstUse = activate(once, PASSWORD).getResponse().getStatus();
        int secondUse = activate(once, "another new passphrase").getResponse().getStatus();
        clock.advance(EmailLinkPolicy.ACTIVATION_LIFETIME.plusSeconds(1));
        int afterExpiry = activate(late, PASSWORD).getResponse().getStatus();

        // Assert
        assertThat(firstUse).isEqualTo(204);
        assertThat(secondUse).isEqualTo(400);
        assertThat(afterExpiry).isEqualTo(400);
        assertThat(login(first.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(first.email(), "another new passphrase").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aWeakPasswordIsRefusedWithoutSpendingTheLink() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);

        // Act
        MvcResult tooShort = activate(link, "short1");
        MvcResult common = activate(link, "password123");
        MvcResult fine = activate(link, PASSWORD);

        // Assert
        assertThat(tooShort.getResponse().getStatus()).isEqualTo(422);
        assertThat(tooShort.getResponse().getContentAsString()).contains("\"fields\":[\"password\"]").doesNotContain("short1");
        assertThat(common.getResponse().getStatus()).isEqualTo(422);
        assertThat(fine.getResponse().getStatus()).isEqualTo(204);
    }

    // ---- refresh -----------------------------------------------------------------------------------------------

    @Test
    void refreshRotatesTheCookieAndTheOldTokenStopsWorkingAfterTheGraceWindow() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        String first = refreshTokenOf(loggedIn);

        // Act
        MvcResult rotated = refresh(first);
        String second = refreshTokenOf(rotated);
        clock.advance(RefreshTokenPolicy.REUSE_GRACE.plusSeconds(1));
        MvcResult replay = refresh(first);
        MvcResult newestAfterReplay = refresh(second);

        // Assert - replay of a rotated token is theft: the family, including the newest token, is dead
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        assertThat(second).isNotEqualTo(first);
        assertThat(me(accessTokenOf(rotated)).getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(newestAfterReplay.getResponse().getStatus()).isEqualTo(401);
        assertThat(logText()).contains("refresh token reuse, family revoked");
    }

    @Test
    void aSecondTabPresentingTheSameCookieWithinTheGraceWindowDoesNotKillTheFamily() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String first = refreshTokenOf(login(founder.email(), PASSWORD));
        String second = refreshTokenOf(refresh(first));
        clock.advance(RefreshTokenPolicy.REUSE_GRACE.minusSeconds(1));

        // Act
        MvcResult duplicate = refresh(first);
        MvcResult stillGood = refresh(second);

        // Assert
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(401);
        assertThat(stillGood.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aLoginIsDeadAfterThirtyIdleDaysAndAfterNinetyDaysInTotal() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String idle = refreshTokenOf(login(founder.email(), PASSWORD));
        String busy = refreshTokenOf(login(founder.email(), PASSWORD));

        // Act - the first sits unused for 31 days; the second is refreshed every 20 days until 100 days in
        clock.advance(Duration.ofDays(31));
        int idleStatus = refresh(idle).getResponse().getStatus();
        for (int i = 0; i < 2; i++) {
            clock.set(START.plus(Duration.ofDays(20L * (i + 1))));
            busy = refreshTokenOf(refresh(busy));
        }
        clock.set(START.plus(Duration.ofDays(91)));
        int absoluteStatus = refresh(busy).getResponse().getStatus();

        // Assert
        assertThat(idleStatus).isEqualTo(401);
        assertThat(absoluteStatus).isEqualTo(401);
    }

    @Test
    void parallelRefreshesOfOneTokenHaveExactlyOneWinnerAndNobodyLosesTheFamily() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String token = refreshTokenOf(login(founder.email(), PASSWORD));
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();

        // Act
        for (int i = 0; i < callers; i++) {
            Callable<MvcResult> call = () -> {
                go.await();
                return refresh(token);
            };
            results.add(pool.submit(call));
        }
        go.countDown();
        List<MvcResult> done = new ArrayList<>();
        for (Future<MvcResult> result : results) {
            done.add(result.get());
        }
        pool.shutdown();

        // Assert
        List<MvcResult> winners = done.stream().filter(r -> r.getResponse().getStatus() == 200).toList();
        assertThat(winners).hasSize(1);
        assertThat(done.stream().filter(r -> r.getResponse().getStatus() == 401)).hasSize(callers - 1);
        assertThat(refresh(refreshTokenOf(winners.get(0))).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void parallelUsesOfOneActivationLinkHaveExactlyOneWinner() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();

        // Act
        for (int i = 0; i < callers; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return activate(link, PASSWORD);
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<MvcResult> result : results) {
            statuses.add(result.get().getResponse().getStatus());
        }
        pool.shutdown();

        // Assert
        assertThat(statuses.stream().filter(status -> status == 204)).hasSize(1);
        assertThat(statuses.stream().filter(status -> status == 400)).hasSize(callers - 1);
    }

    // ---- logout and revocation ---------------------------------------------------------------------------------

    @Test
    void logoutEndsThatLoginOnly() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String laptop = refreshTokenOf(login(founder.email(), PASSWORD));
        String phone = refreshTokenOf(login(founder.email(), PASSWORD));

        // Act
        MvcResult loggedOut = mockMvc.perform(post("/api/v1/auth/logout").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie(COOKIE, laptop)).content("{}")).andReturn();

        // Assert
        assertThat(loggedOut.getResponse().getStatus()).isEqualTo(204);
        assertThat(loggedOut.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(refresh(laptop).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutAllEndsEveryLoginAndKillsTheAccessTokensAtOnce() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        MvcResult laptop = login(founder.email(), PASSWORD);
        MvcResult phone = login(founder.email(), PASSWORD);
        String access = accessTokenOf(laptop);
        assertThat(me(access).getResponse().getStatus()).isEqualTo(200);

        // Act
        MvcResult result = mockMvc.perform(post("/api/v1/auth/logout-all").with(fromNewClient()).header("Authorization", "Bearer " + access)).andReturn();

        // Assert
        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(me(access).getResponse().getStatus()).as("the stamp changed, the token is dead before its expiry").isEqualTo(401);
        assertThat(refresh(refreshTokenOf(laptop)).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(refreshTokenOf(phone)).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(founder.email(), PASSWORD).getResponse().getStatus()).as("a new login is fine").isEqualTo(200);
    }

    @Test
    void aPasswordResetChangesThePasswordRevokesSessionsAndKillsOldAccessTokens() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        String access = accessTokenOf(loggedIn);
        String refresh = refreshTokenOf(loggedIn);

        // Act
        MvcResult requested = postJson("/api/v1/auth/password-reset-requests", json("email", founder.email()));
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.PASSWORD_RESET);
        MvcResult reset = postJson("/api/v1/auth/password-resets", json("token", link.token(), "password", "my brand new passphrase"));

        // Assert
        assertThat(requested.getResponse().getStatus()).isEqualTo(202);
        assertThat(reset.getResponse().getStatus()).isEqualTo(204);
        assertThat(me(access).getResponse().getStatus()).as("old access token, next request").isEqualTo(401);
        assertThat(refresh(refresh).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(founder.email(), PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(founder.email(), "my brand new passphrase").getResponse().getStatus()).isEqualTo(200);
        assertThat(postJson("/api/v1/auth/password-resets", json("token", link.token(), "password", "yet another passphrase"))
                .getResponse().getStatus()).as("single use").isEqualTo(400);
    }

    @Test
    void aResetLinkExpiresAfterThirtyMinutes() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        postJson("/api/v1/auth/password-reset-requests", json("email", founder.email()));
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.PASSWORD_RESET);
        clock.advance(Duration.ofMinutes(31));

        // Act
        MvcResult reset = postJson("/api/v1/auth/password-resets", json("token", link.token(), "password", "my brand new passphrase"));

        // Assert
        assertThat(reset.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void anAccessTokenExpiresAfterTenMinutes() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String access = accessTokenOf(login(founder.email(), PASSWORD));
        clock.advance(AccessTokenPolicy.TTL.plus(AccessTokenPolicy.CLOCK_SKEW).plusSeconds(1));

        // Act
        MvcResult result = me(access);

        // Assert
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aDeactivatedMemberIsOutImmediatelyEverywhere() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        String access = accessTokenOf(loggedIn);
        Member member = members.findById(founder.associationId(), founder.memberId()).orElseThrow();
        members.save(member.deactivate());

        // Act
        int withOldAccessToken = me(access).getResponse().getStatus();
        int refreshing = refresh(refreshTokenOf(loggedIn)).getResponse().getStatus();
        int loggingIn = login(founder.email(), PASSWORD).getResponse().getStatus();

        // Assert
        assertThat(withOldAccessToken).as("still-valid access token").isEqualTo(401);
        assertThat(refreshing).as("refresh").isEqualTo(401);
        assertThat(loggingIn).as("login").isEqualTo(401);
    }

    @Test
    void aTokenNamingAnotherAssociationsMemberIsRejectedEvenWithAValidSignatureAndStamp() throws Exception {
        // Arrange
        Founder a = activatedFounder();
        Founder b = registerFounder();
        UUID userOfA = jdbc.queryForObject("select id from app_user where email = ?", UUID.class, a.email());
        String stamp = jdbc.queryForObject("select security_stamp from app_user where id = ?", String.class, userOfA);

        // Act - correctly signed, right stamp, but A's user with B's tenant and member
        String forged = issuer.issue(userOfA, b.associationId(), b.memberId(), stamp).value();
        String mixed = issuer.issue(userOfA, b.associationId(), a.memberId(), stamp).value();
        String genuine = issuer.issue(userOfA, a.associationId(), a.memberId(), stamp).value();

        // Assert
        assertThat(me(forged).getResponse().getStatus()).isEqualTo(401);
        assertThat(me(mixed).getResponse().getStatus()).isEqualTo(401);
        assertThat(me(genuine).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- uniform answers ---------------------------------------------------------------------------------------

    @Test
    void everyKindOfLoginFailureLooksExactlyTheSame() throws Exception {
        // Arrange
        Founder activated = activatedFounder();
        Founder neverActivated = registerFounder();
        Founder deactivated = activatedFounder();
        members.save(members.findById(deactivated.associationId(), deactivated.memberId()).orElseThrow().deactivate());

        // Act
        MvcResult unknown = login("nobody." + UUID.randomUUID() + "@example.com", PASSWORD);
        MvcResult wrongPassword = login(activated.email(), "not the right passphrase");
        MvcResult notActivated = login(neverActivated.email(), PASSWORD);
        MvcResult inactiveMember = login(deactivated.email(), PASSWORD);
        MvcResult malformed = login("not-an-email", PASSWORD);

        // Assert
        Map<String, Object> expected = shape(unknown);
        assertThat(expected.get("status")).isEqualTo(401);
        assertThat((String) expected.get("body")).contains("INVALID_CREDENTIALS");
        assertThat(shape(wrongPassword)).isEqualTo(expected);
        assertThat(shape(notActivated)).isEqualTo(expected);
        assertThat(shape(inactiveMember)).isEqualTo(expected);
        assertThat(shape(malformed)).isEqualTo(expected);
        assertThat(unknown.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void aResetRequestLooksTheSameForKnownAndUnknownEmails() throws Exception {
        // Arrange
        Founder activated = activatedFounder();

        // Act
        MvcResult known = postJson("/api/v1/auth/password-reset-requests", json("email", activated.email()));
        MvcResult unknown = postJson("/api/v1/auth/password-reset-requests", json("email", "nobody." + UUID.randomUUID() + "@example.com"));
        MvcResult malformed = postJson("/api/v1/auth/password-reset-requests", json("email", "not-an-email"));

        // Assert
        assertThat(shape(known)).isEqualTo(shape(unknown)).isEqualTo(shape(malformed));
        assertThat(known.getResponse().getStatus()).isEqualTo(202);
        assertThat(known.getResponse().getContentAsString()).contains("\"status\":\"RECEIVED\"");
    }

    @Test
    void anUnknownEmailTakesComparableTimeToAKnownOneWithAWrongPassword() throws Exception {
        // Arrange
        Founder activated = activatedFounder();
        login("warm.up@example.com", PASSWORD);
        List<Long> knownTimes = new ArrayList<>();
        List<Long> unknownTimes = new ArrayList<>();

        // Act
        for (int i = 0; i < 4; i++) {
            long t0 = System.nanoTime();
            login(activated.email(), "not the right passphrase " + i);
            knownTimes.add(System.nanoTime() - t0);
            long t1 = System.nanoTime();
            login("nobody." + UUID.randomUUID() + "@example.com", "not the right passphrase " + i);
            unknownTimes.add(System.nanoTime() - t1);
        }

        // Assert - the unknown email still costs a full hash verification: the same order of magnitude, not a fast path
        long knownBest = knownTimes.stream().mapToLong(Long::longValue).min().orElseThrow();
        long unknownBest = unknownTimes.stream().mapToLong(Long::longValue).min().orElseThrow();
        assertThat(unknownBest).isGreaterThan(knownBest / 3);
        assertThat(knownBest).isGreaterThan(unknownBest / 3);
    }

    // ---- throttling --------------------------------------------------------------------------------------------

    @Test
    void theSixthLoginAttemptForAnEmailIsThrottledWhetherOrNotItExists() throws Exception {
        // Arrange
        String email = "nobody." + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < RateLimitRule.LOGIN_EMAIL.capacity(); i++) {
            assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(401);
        }

        // Act
        MvcResult throttled = login(email, PASSWORD);

        // Assert
        assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
        assertThat(throttled.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(throttled.getResponse().getContentAsString()).contains("TOO_MANY_REQUESTS");
    }

    @Test
    void theFourthResetRequestForAnEmailIsThrottled() throws Exception {
        // Arrange
        String email = "nobody." + UUID.randomUUID() + "@example.com";
        for (int i = 0; i < RateLimitRule.RESET_EMAIL.capacity(); i++) {
            assertThat(postJson("/api/v1/auth/password-reset-requests", json("email", email)).getResponse().getStatus()).isEqualTo(202);
        }

        // Act
        MvcResult throttled = postJson("/api/v1/auth/password-reset-requests", json("email", email));

        // Assert
        assertThat(throttled.getResponse().getStatus()).isEqualTo(429);
        assertThat(throttled.getResponse().getHeader("Retry-After")).isNotNull();
    }

    // ---- secrets at rest, logs, cookie ---------------------------------------------------------------------------

    @Test
    void passwordsAreArgon2idHashesAndEverySecretIsStoredOnlyAsAHash() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);
        activate(link, PASSWORD);
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        String refreshToken = refreshTokenOf(loggedIn);

        // Act
        Map<String, Object> user = jdbc.queryForMap("select password_hash, security_stamp from app_user where email = ?", founder.email());
        String storedLinkHash = jdbc.queryForObject("select token_hash from email_link where id = ?", String.class, link.reference());
        List<String> refreshHashes = jdbc.queryForList("select token_hash from refresh_token", String.class);

        // Assert
        assertThat((String) user.get("password_hash")).startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$").doesNotContain(PASSWORD);
        assertThat((String) user.get("security_stamp")).hasSize(43);
        assertThat(storedLinkHash).hasSize(64).isNotEqualTo(link.token());
        assertThat(refreshHashes).contains(SecureTokens.sha256(refreshToken)).doesNotContain(refreshToken);
    }

    @Test
    void theRefreshCookieIsHttpOnlySecureStrictAndScopedToTheAuthRoutes() throws Exception {
        // Arrange
        Founder founder = activatedFounder();

        // Act
        String cookie = login(founder.email(), PASSWORD).getResponse().getHeader(HttpHeaders.SET_COOKIE);

        // Assert
        assertThat(cookie).startsWith(COOKIE + "=").contains("HttpOnly").contains("Secure").contains("SameSite=Strict")
                .contains("Path=/api/v1/auth").contains("Max-Age=" + Duration.ofDays(30).toSeconds()).doesNotContain("Domain");
    }

    @Test
    void theCookieEndpointsRefuseAForeignOriginAndANonJsonBodyEvenInTheFullApp() throws Exception {
        // Arrange
        Founder founder = activatedFounder();
        String token = refreshTokenOf(login(founder.email(), PASSWORD));

        // Act
        MvcResult foreign = mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON)
                .header("Origin", "https://evil.example").cookie(new Cookie(COOKIE, token)).content("{}")).andReturn();
        MvcResult form = mockMvc.perform(post("/api/v1/auth/refresh").with(fromNewClient()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .cookie(new Cookie(COOKIE, token))).andReturn();

        // Assert - and the refused calls did not rotate the token
        assertThat(foreign.getResponse().getStatus()).isEqualTo(403);
        assertThat(form.getResponse().getStatus()).isEqualTo(415);
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void noPersonalDataPasswordOrTokenReachesTheLogsAcrossAWholeJourney() throws Exception {
        // Arrange
        Founder founder = registerFounder();
        AccountLink link = sentLinkTo(founder.email(), AccountLinkPurpose.ACTIVATION);
        String wrongPassword = "definitely not the passphrase";

        // Act
        activate(link, "short1");
        activate(link, PASSWORD);
        login(founder.email(), wrongPassword);
        login("ghost@example.com", wrongPassword);
        MvcResult loggedIn = login(founder.email(), PASSWORD);
        String access = accessTokenOf(loggedIn);
        String refreshToken = refreshTokenOf(loggedIn);
        String rotated = refreshTokenOf(refresh(refreshToken));
        clock.advance(Duration.ofMinutes(1));
        refresh(refreshToken);
        postJson("/api/v1/auth/password-reset-requests", json("email", founder.email()));
        AccountLink reset = sentLinkTo(founder.email(), AccountLinkPurpose.PASSWORD_RESET);
        postJson("/api/v1/auth/password-resets", json("token", reset.token(), "password", "a different passphrase"));
        me(access);

        // Assert
        String text = logText();
        assertThat(text).isNotBlank();
        for (String secret : List.of(founder.email(), "ghost@example.com", PASSWORD, wrongPassword, "a different passphrase", link.token(),
                reset.token(), access, refreshToken, rotated, "Founder Name")) {
            assertThat(text).as("the log must not contain a secret or personal value").doesNotContain(secret);
        }
        assertThat(text).as("only truncated client addresses").doesNotContainPattern("\\b10\\.\\d+\\.\\d+\\.(?!0/24)\\d+");
        assertThat(text).doesNotContain("Bearer ");
    }
}
