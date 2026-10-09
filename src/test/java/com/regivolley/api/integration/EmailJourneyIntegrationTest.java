package com.regivolley.api.integration;

import com.jayway.jsonpath.JsonPath;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.testsupport.LogCapture;
import com.regivolley.testsupport.SmtpTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #40 end to end: the real application (filter chain, services, PostgreSQL) with the SMTP adapters switched on against an
 * in-process SMTP server. A founder registers, activates the account from the emailed link, forgets the password and resets it from the
 * emailed link; an applicant is approved and another rejected. Nothing is mocked between the HTTP request and the SMTP socket.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailJourneyIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String ORIGIN = "https://app.example.org";
    private static final Pattern LINK = Pattern.compile("(" + ORIGIN + "/(?:activate|reset-password))#token=(\\S+)");
    private static final SmtpTestServer SMTP = new SmtpTestServer().start();
    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger(1);
    private static final String PASSWORD = "correct horse battery staple";

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry registry) {
        registry.add("regi-volley.mail.host", () -> "127.0.0.1");
        registry.add("regi-volley.mail.port", SMTP::port);
        registry.add("regi-volley.mail.security", () -> "NONE");
        registry.add("regi-volley.mail.from", () -> "RegiVolley <no-reply@example.org>");
        registry.add("regi-volley.security.web-origin", () -> ORIGIN);
    }

    @AfterAll
    static void stopSmtp() {
        SMTP.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    private MvcResult post(String path, String body) throws Exception {
        int n = NEXT_CLIENT.getAndIncrement();
        return mockMvc.perform(MockMvcRequestBuilders.post(path).with(request -> {
            request.setRemoteAddr("10.40." + (n / 250) + "." + (n % 250 + 1));
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult postWithToken(String path, String token, String body) throws Exception {
        int n = NEXT_CLIENT.getAndIncrement();
        return mockMvc.perform(MockMvcRequestBuilders.post(path).with(request -> {
            request.setRemoteAddr("10.40." + (n / 250) + "." + (n % 250 + 1));
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private MvcResult get(String path, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Waits for a mail to the address with this subject and returns it. */
    private static SmtpTestServer.Received mailTo(String address, String subject) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            List<SmtpTestServer.Received> found = SMTP.received().stream()
                    .filter(mail -> mail.to().contains(address) && mail.subject().equals(subject)).toList();
            if (!found.isEmpty()) {
                return found.get(found.size() - 1);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No mail \"" + subject + "\" to " + address + " arrived");
    }

    private static String tokenIn(SmtpTestServer.Received mail, String route) {
        Matcher matcher = LINK.matcher(mail.text());
        assertThat(matcher.find()).as("a link in the text body").isTrue();
        assertThat(matcher.group(1)).isEqualTo(ORIGIN + route);
        String token = UriUtils.decode(matcher.group(2), StandardCharsets.UTF_8);
        assertThat(mail.html()).contains("href=\"" + ORIGIN + route + "#token=" + matcher.group(2) + "\"");
        return token;
    }

    private String registerBody(String shortName, String founderEmail) {
        return "{\"name\":\"<script>alert(1)</script> Club " + shortName + "\",\"shortName\":\"" + shortName + "\",\"locality\":\"Lisbon\","
                + "\"contactEmail\":\"info@" + shortName + ".example\",\"levelNames\":[\"Beginner\"],"
                + "\"founderName\":\"Founder\",\"founderEmail\":\"" + founderEmail + "\",\"founderPhone\":\"912345678\","
                + "\"consentAccepted\":true,\"policyVersion\":\"2026-01\"}";
    }

    private String loginToken(String email, String password) throws Exception {
        MvcResult login = post("/api/v1/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertThat(login.getResponse().getStatus()).as(login.getResponse().getContentAsString()).isEqualTo(200);
        return JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test
    void aFounderActivatesFromTheEmailedLinkAndLaterResetsThePasswordFromAnother() throws Exception {
        // Arrange
        String shortName = "mail-" + unique();
        String email = "founder." + unique() + "@example.com";
        try (LogCapture logs = new LogCapture()) {
            // Act - register: the activation link arrives at the founder's address
            assertThat(post("/api/v1/public/associations", registerBody(shortName, email)).getResponse().getStatus()).isEqualTo(201);
            SmtpTestServer.Received activation = mailTo(email, "Activate your RegiVolley account");
            String activationToken = tokenIn(activation, "/activate");
            assertThat(activation.html()).doesNotContain("<script>");
            assertThat(post("/api/v1/auth/activate", "{\"token\":\"" + activationToken + "\",\"password\":\"" + PASSWORD + "\"}")
                    .getResponse().getStatus()).isEqualTo(204);
            loginToken(email, PASSWORD);

            // Act - forgot the password: the reset link arrives at the same account address
            assertThat(post("/api/v1/auth/password-reset-requests", "{\"email\":\"" + email + "\"}").getResponse().getStatus()).isEqualTo(202);
            SmtpTestServer.Received reset = mailTo(email, "Reset your RegiVolley password");
            String resetToken = tokenIn(reset, "/reset-password");
            String newPassword = "a different long passphrase";
            assertThat(post("/api/v1/auth/password-resets", "{\"token\":\"" + resetToken + "\",\"password\":\"" + newPassword + "\"}")
                    .getResponse().getStatus()).isEqualTo(204);
            loginToken(email, newPassword);

            // Assert - the links work once, and neither token nor address reached a log
            assertThat(post("/api/v1/auth/activate", "{\"token\":\"" + activationToken + "\",\"password\":\"" + PASSWORD + "\"}")
                    .getResponse().getStatus()).isEqualTo(400);
            assertThat(logs.everything()).doesNotContain(activationToken).doesNotContain(resetToken).doesNotContain(email);
        }
    }

    @Test
    void anApprovedApplicantIsWelcomedAndGetsAnActivationLinkAndARejectedOneIsToldToo() throws Exception {
        // Arrange - a founder with an association
        String shortName = "join-" + unique();
        String founder = "founder." + unique() + "@example.com";
        post("/api/v1/public/associations", registerBody(shortName, founder));
        String adminToken = loginAfterActivation(founder);
        String accepted = "rita." + unique() + "@example.com";
        String refused = "paulo." + unique() + "@example.com";
        for (String applicant : List.of(accepted, refused)) {
            assertThat(post("/api/v1/public/associations/" + shortName + "/join-requests", "{\"name\":\"Visitor <b>x</b>\",\"email\":\""
                    + applicant + "\",\"phone\":\"912345679\",\"consentAccepted\":true,\"policyVersion\":\"2026-01\"}")
                    .getResponse().getStatus()).isEqualTo(202);
        }
        String pending = get("/api/v1/join-requests", adminToken).getResponse().getContentAsString();
        String acceptedId = ((List<String>) JsonPath.read(pending, "$[?(@.email == '" + accepted + "')].id")).get(0);
        String refusedId = ((List<String>) JsonPath.read(pending, "$[?(@.email == '" + refused + "')].id")).get(0);

        // Act
        assertThat(postWithToken("/api/v1/join-requests/" + acceptedId + "/approval", adminToken, "{}").getResponse().getStatus()).isEqualTo(200);
        assertThat(postWithToken("/api/v1/join-requests/" + refusedId + "/rejection", adminToken, "{\"reason\":\"Full\"}").getResponse().getStatus())
                .isEqualTo(200);

        // Assert
        SmtpTestServer.Received welcome = mailTo(accepted, "Your request to join was approved");
        assertThat(welcome.text()).contains("Club " + shortName).doesNotContain("Visitor");
        assertThat(welcome.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt; Club " + shortName).doesNotContain("<script>");
        tokenIn(mailTo(accepted, "Activate your RegiVolley account"), "/activate");
        SmtpTestServer.Received rejection = mailTo(refused, "Your request to join was not approved");
        assertThat(rejection.text()).doesNotContain("Full").doesNotContain("Visitor");
    }

    private String loginAfterActivation(String email) throws Exception {
        String token = tokenIn(mailTo(email, "Activate your RegiVolley account"), "/activate");
        assertThat(post("/api/v1/auth/activate", "{\"token\":\"" + token + "\",\"password\":\"" + PASSWORD + "\"}").getResponse().getStatus())
                .isEqualTo(204);
        return loginToken(email, PASSWORD);
    }

    @Test
    void theFourthRegistrationOfTheDayForOneFoundersEmailIsRefusedAndSendsNothing() throws Exception {
        // Arrange
        String email = "flooded." + unique() + "@example.com";
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/v1/public/associations", registerBody("flood-" + unique(), email)).getResponse().getStatus()).isEqualTo(201);
        }
        for (int i = 0; i < 200 && mailsTo(email) < 3; i++) {
            Thread.sleep(50);
        }
        assertThat(mailsTo(email)).isEqualTo(3);

        // Act
        MvcResult fourth = post("/api/v1/public/associations", registerBody("flood-" + unique(), email));

        // Assert
        assertThat(fourth.getResponse().getStatus()).isEqualTo(429);
        assertThat(fourth.getResponse().getHeader("Retry-After")).isNotBlank();
        Thread.sleep(300);
        assertThat(mailsTo(email)).isEqualTo(3);
    }

    private static long mailsTo(String address) {
        return SMTP.received().stream().filter(mail -> mail.to().contains(address)).count();
    }
}
