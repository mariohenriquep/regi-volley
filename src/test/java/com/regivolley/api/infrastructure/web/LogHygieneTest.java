package com.regivolley.api.infrastructure.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.infrastructure.security.AbstractSecuredWebTest;
import com.regivolley.testsupport.FailingTestController;
import com.regivolley.testsupport.ValidatedTestController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Log-capture test (threat model E2, section 10.9): whatever a client sends or a failure quotes - a rejected email, a password,
 * a token, an Authorization header - never reaches a log line, while the names of the offending fields do.
 */
@Import({FailingTestController.class, ValidatedTestController.class})
class LogHygieneTest extends AbstractSecuredWebTest {

    private static final String EMAIL = "leak-me@@example.com";
    private static final String PASSWORD = "hunter2-s3cr3t";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger root;
    private Level previousLevel;

    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        previousLevel = root.getLevel();
        root.setLevel(Level.INFO);
        appender.start();
        root.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        root.detachAppender(appender);
        root.setLevel(previousLevel);
    }

    /** Everything the logging system saw: formatted messages and the messages of every throwable in the chain. */
    private String everythingLogged() {
        List<String> lines = new ArrayList<>();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            lines.add(event.getFormattedMessage());
            for (IThrowableProxy proxy = event.getThrowableProxy(); proxy != null; proxy = proxy.getCause()) {
                lines.add(proxy.getClassName() + ": " + proxy.getMessage());
            }
            lines.add(String.valueOf(event.getMDCPropertyMap()));
        }
        return String.join("\n", lines);
    }

    private void assertNothingSensitiveLogged() {
        assertThat(everythingLogged()).doesNotContain(EMAIL).doesNotContain("leak-me").doesNotContain(PASSWORD)
                .doesNotContain("hunter2").doesNotContain("s3cr3t").doesNotContain("Bearer ");
    }

    @Test
    void aRejectedEmailValueIsNeverLoggedButTheFieldNameIs() throws Exception {
        // Arrange
        String body = "{\"email\":\"" + EMAIL + "\",\"note\":\"" + PASSWORD + "\"}";

        // Act
        mockMvc.perform(post("/api/v1/test/validated").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));

        // Assert
        assertNothingSensitiveLogged();
        assertThat(everythingLogged()).contains("fields=[email, note]");
    }

    @Test
    void malformedJsonQuotingAnEmailIsNeverLogged() throws Exception {
        // Arrange
        String body = "{\"email\":\"" + EMAIL + "\", broken";

        // Act
        mockMvc.perform(post("/api/v1/test/validated").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));

        // Assert
        assertNothingSensitiveLogged();
    }

    @Test
    void anUnknownPropertyCarryingAPasswordIsNeverLogged() throws Exception {
        // Arrange
        String body = "{\"email\":\"ana@example.com\",\"password\":\"" + PASSWORD + "\"}";

        // Act
        mockMvc.perform(post("/api/v1/test/validated").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));

        // Assert
        assertNothingSensitiveLogged();
    }

    @Test
    void aWronglyTypedValueIsNeverLogged() throws Exception {
        // Arrange
        String body = "{\"id\":\"" + PASSWORD + "\"}";

        // Act
        mockMvc.perform(post("/api/v1/test/typed").header("Authorization", bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(get("/api/v1/test/by-id/" + PASSWORD).header("Authorization", bearer()));

        // Assert
        assertNothingSensitiveLogged();
    }

    @Test
    void anUnexpectedFailureLogsItsClassAndPlaceNotItsMessage() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        mockMvc.perform(get("/api/v1/test/fail/runtime").header("Authorization", authorization));
        mockMvc.perform(get("/api/v1/test/fail/data-integrity").header("Authorization", authorization));

        // Assert
        assertThat(everythingLogged()).doesNotContain(FailingTestController.SECRET_MESSAGE)
                .doesNotContain("ana.silva").doesNotContain("hunter2").doesNotContain("SQL");
        assertThat(everythingLogged()).contains("class=java.lang.IllegalStateException").contains("FailingTestController");
    }

    @Test
    void aConstraintOnAPathVariableNeverLogsTheValue() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        mockMvc.perform(get("/api/v1/test/named/" + PASSWORD).header("Authorization", authorization));

        // Assert
        assertNothingSensitiveLogged();
        assertThat(everythingLogged()).contains("HandlerMethodValidationException");
    }

    @Test
    void aConstraintViolationFromAProxiedControllerLogsTheParameterNameNotTheValue() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        mockMvc.perform(get("/api/v1/test-validated/named/" + PASSWORD).header("Authorization", authorization));

        // Assert
        assertNothingSensitiveLogged();
        assertThat(everythingLogged()).contains("fields=[named.name]");
    }

    @Test
    void aFrameworkServerErrorLogsItsClassAndNotItsMessage() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        mockMvc.perform(get("/api/v1/test/missing-path/" + PASSWORD).header("Authorization", authorization));

        // Assert
        assertNothingSensitiveLogged();
        assertThat(everythingLogged()).contains("MissingPathVariableException");
    }

    @Test
    void aSecurityExceptionMessageIsNeverLogged() throws Exception {
        // Arrange
        String authorization = bearer();

        // Act
        mockMvc.perform(get("/api/v1/test/fail/authentication").header("Authorization", authorization));
        mockMvc.perform(get("/api/v1/test/fail/access-denied").header("Authorization", authorization));

        // Assert
        assertNothingSensitiveLogged();
    }

    @Test
    void tokensAndAuthorizationHeadersNeverReachTheLog() throws Exception {
        // Arrange
        String genuine = validToken();

        // Act
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + genuine));
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer forged.token." + PASSWORD));
        mockMvc.perform(get("/api/v1/me").queryParam("access_token", genuine));

        // Assert
        assertThat(everythingLogged()).doesNotContain(genuine).doesNotContain("forged.token");
        assertNothingSensitiveLogged();
    }

    @Test
    void aRejectedPrincipalIsLoggedByIdsAndReasonOnly() throws Exception {
        // Arrange
        accounts.clear();

        // Act
        mockMvc.perform(get("/api/v1/me").header("Authorization", bearer()));

        // Assert
        assertThat(everythingLogged()).contains("reason=UNKNOWN_ACCOUNT").contains(member.id().toString())
                .doesNotContain("Ana").doesNotContain("ana.silva").doesNotContain("912345678");
    }
}
