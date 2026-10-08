package com.regivolley.api.infrastructure.web;

import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.infrastructure.security.AbstractSecuredWebTest;
import com.regivolley.api.infrastructure.web.exception.ApiExceptionHandler;
import com.regivolley.testsupport.FailingTestController;
import com.regivolley.testsupport.ValidatedTestController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One test per row of the exception-to-HTTP table of the threat model (section 7, section 10.9). */
@Import({FailingTestController.class, ValidatedTestController.class})
class ApiExceptionHandlerTest extends AbstractSecuredWebTest {

    private ResultActions failing(String kind) throws Exception {
        return mockMvc.perform(get("/api/v1/test/fail/" + kind).header("Authorization", bearer()));
    }

    private ResultActions postJson(String path, String body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void notAllowedIs403WithItsOwnMessageAndWinsOverTheGenericBusinessRule() throws Exception {
        // Arrange
        String kind = "not-allowed";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("You are not allowed to cancel this booking"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"member-not-found", "session-not-found", "short-name-not-found"})
    void everyNotFoundIs404WithAGenericBodyThatEchoesNoId(String kind) throws Exception {
        // Arrange
        String path = kind;

        // Act
        ResultActions result = failing(path);

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(content().string(not(containsString("my-club"))));
    }

    @Test
    void aJoinRequestThatIsNotPossibleLooksLikeSuccess() throws Exception {
        // Arrange
        String kind = "join-request-not-possible";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(status().isAccepted()).andExpect(content().json("{\"status\":\"RECEIVED\"}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short-name-taken", "email-used", "last-admin", "duplicate-booking", "concurrent"})
    void theConflictsAre409(String kind) throws Exception {
        // Arrange
        String path = kind;

        // Act
        ResultActions result = failing(path);

        // Assert
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void aConcurrentModificationIsAGenericConflictWithoutIds() throws Exception {
        // Arrange
        String kind = "concurrent";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(jsonPath("$.message").value("The data was changed by someone else in the meantime. Reload and try again"));
    }

    @Test
    void aBusinessConflictShowsItsEnglishMessage() throws Exception {
        // Arrange
        String kind = "last-admin";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(jsonPath("$.message").value("The association must keep at least one active administrator"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"business-rule", "anonymised"})
    void otherBusinessRulesAre422WithTheirEnglishMessage(String kind) throws Exception {
        // Arrange
        String path = kind;

        // Act
        ResultActions result = failing(path);

        // Assert
        result.andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void aBusinessRuleShowsItsOwnMessageAsIs() throws Exception {
        // Arrange
        String expected = new CoachCannotBookOwnSessionException().getMessage();

        // Act
        ResultActions result = failing("business-rule");

        // Assert
        result.andExpect(jsonPath("$.message").value(expected));
    }

    @Test
    void anInvalidFieldIs422AndNamesTheField() throws Exception {
        // Arrange
        String kind = "invalid-field";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.fields[0]").value("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invariant", "runtime", "data-integrity"})
    void invariantsAndUnexpectedErrorsAreAGeneric500WithNoExceptionText(String kind) throws Exception {
        // Arrange
        String path = kind;

        // Act
        ResultActions result = failing(path);

        // Assert
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString(FailingTestController.SECRET_MESSAGE))))
                .andExpect(content().string(not(containsString("ana.silva"))))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void anAccessDeniedThrownFromAHandlerStays403() throws Exception {
        // Arrange
        String kind = "access-denied";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void anAuthenticationFailureThrownFromAHandlerStays401() throws Exception {
        // Arrange
        String kind = "authentication";

        // Act
        ResultActions result = failing(kind);

        // Assert
        result.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void validationNamesTheFieldsButNeverTheRejectedValues() throws Exception {
        // Arrange
        String body = "{\"email\":\"not-an-email-s3cr3t\",\"note\":\"far too long a note\"}";

        // Act
        ResultActions result = postJson("/api/v1/test/validated", body);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.fields[0]").value("email"))
                .andExpect(jsonPath("$.fields[1]").value("note"))
                .andExpect(content().string(not(containsString("s3cr3t"))))
                .andExpect(content().string(not(containsString("far too long"))));
    }

    @Test
    void aConstraintOnAPathVariableIs400WithoutTheValue() throws Exception {
        // Arrange
        String path = "/api/v1/test/named/much-too-long-s3cr3t";

        // Act
        ResultActions result = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert - Spring's built-in method validation: HandlerMethodValidationException
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("s3cr3t"))));
    }

    @Test
    void aConstraintViolationFromAProxiedControllerIs400WithTheParameterNameButNotTheValue() throws Exception {
        // Arrange
        String path = "/api/v1/test-validated/named/much-too-long-s3cr3t";

        // Act
        ResultActions result = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fields[0]").value("named.name"))
                .andExpect(content().string(not(containsString("s3cr3t"))));
    }

    @Test
    void aServerSideFrameworkErrorIsAGeneric500() throws Exception {
        // Arrange - the route template and the method disagree about a path variable: our bug, not the client's
        String path = "/api/v1/test/missing-path/x";

        // Act
        ResultActions result = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        // Arrange
        String body = "{\"email\": ";

        // Act
        ResultActions result = postJson("/api/v1/test/validated", body);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void anEmptyBodyIs400() throws Exception {
        // Arrange
        String path = "/api/v1/test/validated";

        // Act
        ResultActions result = mockMvc.perform(post(path).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON));

        // Assert
        result.andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownPropertyIs400NotSilentlyDropped() throws Exception {
        // Arrange - mass assignment attempt (M3): fields the DTO does not declare
        String body = "{\"email\":\"ana@example.com\",\"roles\":[\"ADMIN\"],\"associationId\":\"x\"}";

        // Act
        ResultActions result = postJson("/api/v1/test/validated", body);

        // Assert
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("ADMIN"))));
    }

    @Test
    void aWronglyTypedBodyValueIs400WithoutEchoingIt() throws Exception {
        // Arrange
        String body = "{\"id\":\"not-a-uuid-s3cr3t\"}";

        // Act
        ResultActions result = postJson("/api/v1/test/typed", body);

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("s3cr3t"))));
    }

    @Test
    void aWronglyTypedPathVariableIs400WithoutEchoingIt() throws Exception {
        // Arrange
        String path = "/api/v1/test/by-id/not-a-uuid-s3cr3t";

        // Act
        ResultActions result = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert
        result.andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("s3cr3t"))));
    }

    @Test
    void aWrongContentTypeIs415() throws Exception {
        // Arrange
        String path = "/api/v1/test/validated";

        // Act
        ResultActions result = mockMvc.perform(post(path).header("Authorization", bearer()).contentType(MediaType.TEXT_PLAIN).content("x"));

        // Assert
        result.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void aWrongMethodIs405() throws Exception {
        // Arrange
        String path = "/api/v1/me";

        // Act
        ResultActions result = mockMvc.perform(post(path).header("Authorization", bearer()));

        // Assert
        result.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void anUnknownRouteForAnAuthenticatedCallerIs404InTheSameShape() throws Exception {
        // Arrange
        String path = "/api/v1/nothing-here";

        // Act
        ResultActions result = mockMvc.perform(get(path).header("Authorization", bearer()));

        // Assert
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void everyNotFoundExceptionOfTheDomainIsMappedByTheNotFoundHandler() throws IOException {
        // Arrange - a new *NotFoundException must not fall through to a 500
        Method handler = handlerMethod("handleNotFound");
        Set<String> mapped = Arrays.stream(handler.getAnnotation(ExceptionHandler.class).value())
                .map(Class::getSimpleName).collect(Collectors.toSet());
        List<String> notFoundInDomain;
        try (Stream<Path> files = Files.list(Path.of("src/main/java/com/regivolley/api/domain/exception"))) {
            notFoundInDomain = files.map(path -> path.getFileName().toString().replace(".java", ""))
                    .filter(name -> name.endsWith("NotFoundException")).toList();
        }

        // Act
        List<String> unmapped = notFoundInDomain.stream().filter(name -> !mapped.contains(name)).toList();

        // Assert
        assertThat(notFoundInDomain).isNotEmpty();
        assertThat(unmapped).isEmpty();
    }

    @Test
    void everyConcurrentModificationIsHandledThroughTheAbstractBase() {
        // Arrange
        Method handler = handlerMethod("handleConcurrentModification");

        // Act
        Class<?> handled = handler.getAnnotation(ExceptionHandler.class).value()[0];

        // Assert
        assertThat(handled).isEqualTo(AggregateModifiedConcurrentlyException.class);
    }

    private static Method handlerMethod(String name) {
        return Arrays.stream(ApiExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    }
}
