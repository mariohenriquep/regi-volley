package com.regivolley.api.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Threat model 10.9 and M5 over a whole HTTP journey, errors included: nothing personal reaches the log (names, emails, phone numbers,
 * passwords, tokens, the Authorization header), while the audit lines for money and roles are there and name ids only.
 */
class LogHygieneJourneyIntegrationTest extends AbstractApiIntegrationTest {

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void attach() {
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void detach() {
        root.detachAppender(logs);
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

    @Test
    void aJourneyWithEveryKindOfErrorLeavesNoPersonalDataInTheLogAndTheAuditLinesNameIdsOnly() throws Exception {
        // Arrange
        String founderEmail = "mirela.founder." + unique() + "@example.com";
        String shortName = "log-" + unique();
        String secret = "Zq7-unmistakable-secret-token";
        expect(201, HttpMethod.POST, "/api/v1/public/associations", null, registerBody(shortName, founderEmail));
        String founderLink = sentLinkTo(founderEmail, AccountLinkPurpose.ACTIVATION).token();
        String admin = activateAndLogin(founderEmail);
        String adminId = read(expect(200, HttpMethod.GET, "/api/v1/me", admin, null), "$.memberId");

        String memberName = "Quincas Borba";
        String memberEmail = "quincas.borba." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + shortName + "/join-requests", null, joinBody(memberName, memberEmail));
        String requestId = read(expect(200, HttpMethod.GET, "/api/v1/join-requests", admin, null), "$[0].id");
        String memberId = read(expect(200, HttpMethod.POST, "/api/v1/join-requests/" + requestId + "/approval", admin, null), "$.memberId");
        String memberLink = sentLinkTo(memberEmail, AccountLinkPurpose.ACTIVATION).token();
        String member = activateAndLogin(memberEmail);

        String plan = read(expect(201, HttpMethod.POST, "/api/v1/plans", admin, "{\"name\":\"Monthly\",\"type\":\"MONTHLY_UNLIMITED\",\"priceCents\":3000}"), "$.id");
        String subscription = read(expect(201, HttpMethod.POST, "/api/v1/members/" + memberId + "/subscriptions", admin,
                "{\"planId\":\"" + plan + "\",\"startDate\":\"2026-10-12\"}"), "$.id");
        String paymentId = read(expect(201, HttpMethod.POST, "/api/v1/subscriptions/" + subscription + "/payments", admin,
                "{\"amountCents\":1000,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}"), "$.payment.id");
        expect(201, HttpMethod.POST, "/api/v1/payments/" + paymentId + "/reversal", admin, null);
        expect(200, HttpMethod.PUT, "/api/v1/members/" + memberId + "/roles/COACH", admin, null);
        expect(200, HttpMethod.DELETE, "/api/v1/members/" + memberId + "/roles/COACH", admin, null);

        // Act - every kind of refusal, with personal data in the requests
        String badEmail = "not-an-email-" + secret;
        send(HttpMethod.POST, "/api/v1/public/associations/" + shortName + "/join-requests", null, joinBody("Zeferino Mau", badEmail));
        send(HttpMethod.POST, "/api/v1/auth/login", null, json("email", memberEmail, "password", "wrong-password-" + secret));
        send(HttpMethod.POST, "/api/v1/auth/activate", null, json("token", "bogus-" + secret, "password", PASSWORD));
        send(HttpMethod.POST, "/api/v1/levels", member, "{\"name\":\"Nope\"}");
        send(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=OVERDUE", member);
        send(HttpMethod.PUT, "/api/v1/venues/00000000-0000-4000-8000-000000000001", admin, "{\"name\":\"X\",\"address\":\"Y\",\"courts\":1}");
        send(HttpMethod.POST, "/api/v1/plans", admin, "{\"name\":\"" + secret + "\",\"type\":\"PACK\",\"priceCents\":1}");
        send(HttpMethod.POST, "/api/v1/plans", admin, "{\"name\":\"" + secret + "\",\"type\":\"NOT_A_TYPE\",\"priceCents\":1");
        send(HttpMethod.GET, "/api/v1/sessions?weekOf=" + secret, member);
        send(HttpMethod.GET, "/api/v1/me", "forged." + secret + ".token");
        expect(200, HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PENDING", admin, null);
        expect(200, HttpMethod.POST, "/api/v1/members/" + memberId + "/deactivation", admin, null);
        MvcResult adminLogin = expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", founderEmail, "password", PASSWORD));
        String refresh = refreshCookie(adminLogin);
        mockMvc.perform(post("/api/v1/auth/logout").with(fromNewClient()).contentType(MediaType.APPLICATION_JSON).cookie(new Cookie("__Secure-rt", refresh)).content("{}"));
        String text = logText();

        // Assert - nothing personal, nothing secret
        assertThat(text).doesNotContain(founderEmail).doesNotContain("mirela.founder").doesNotContain("Founder " + shortName)
                .doesNotContain(memberEmail).doesNotContain("quincas.borba").doesNotContain(memberName).doesNotContain("Zeferino")
                .doesNotContain("912345678").doesNotContain("912345679")
                .doesNotContain(PASSWORD).doesNotContain(secret).doesNotContain("wrong-password")
                .doesNotContain(founderLink).doesNotContain(memberLink).doesNotContain(admin).doesNotContain(member).doesNotContain(refresh)
                .doesNotContain("Bearer").doesNotContain("Authorization").doesNotContain("info@" + shortName);

        // the audit lines of threat model M5 are there, by id
        assertThat(text).contains("Payment recorded: associationId=").contains("recordedBy=" + adminId).contains("paymentId=" + paymentId);
        assertThat(text).contains("Payment reversed: associationId=").contains("reversedBy=" + adminId);
        assertThat(text).contains("Role granted: associationId=").contains("memberId=" + memberId).contains("role=COACH").contains("grantedBy=" + adminId);
        assertThat(text).contains("Role revoked: associationId=").contains("revokedBy=" + adminId);
        assertThat(text).contains("Payment list exported: associationId=").contains("exportedBy=" + adminId).contains("status=PENDING").contains("rows=1");
        assertThat(text).contains("Member deactivated: associationId=").contains("deactivatedBy=" + adminId);
    }

    private static String refreshCookie(MvcResult login) {
        String header = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        Matcher matcher = Pattern.compile("__Secure-rt=([^;]*);").matcher(header);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
