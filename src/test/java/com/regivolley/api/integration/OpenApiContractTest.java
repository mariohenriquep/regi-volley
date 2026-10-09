package com.regivolley.api.integration;

import com.regivolley.api.infrastructure.security.PublicRoutes;
import com.regivolley.api.infrastructure.web.dto.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI description of the API, {@code docs/api/openapi.json}, is generated from the controllers by springdoc and checked in:
 * this test regenerates it and fails when the committed file differs, so the description cannot drift from the code (a new route,
 * a changed DTO, a new status all show up here). springdoc is a test-scope dependency, so the running application has no
 * documentation endpoint at all - nothing to switch off in production; the endpoint exists only in this test's context.
 *
 * <p>Regenerate after an intended change with {@code ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true} and commit the file.
 */
@SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.paths-to-match=/api/**", "springdoc.default-produces-media-type=application/json"})
@Import(OpenApiContractTest.Description.class)
class OpenApiContractTest extends AbstractApiIntegrationTest {

    private static final Path COMMITTED = Path.of("docs", "api", "openapi.json");
    private static final String BEARER = "bearerAuth";

    /** What the controllers cannot say by themselves: who the API is, how to authenticate, and the one error body every failure shares. */
    @TestConfiguration
    static class Description {

        @Bean
        OpenApiCustomizer regiVolleyDescription() {
            return openApi -> {
                openApi.info(new Info().title("RegiVolley API").version("v1").description("""
                        Session-by-session booking for amateur indoor volleyball associations. All paths are under /api/v1. The tenant
                        (association) and the caller always come from the bearer token, never from a path, query or body; an id that
                        belongs to another association answers exactly like an id that does not exist (404). Every failure has the body
                        described by ApiError. Times are ISO-8601 instants (UTC); weekly schedules and dates are Europe/Lisbon local time.
                        Money is in cents."""));
                Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
                openApi.components(components);
                components.addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("The short-lived access token of POST /auth/login or /auth/refresh."));
                ModelConverters.getInstance().readAll(ApiError.class).forEach(components::addSchemas);
                addSharedResponses(components);
                openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> describe(path, method, operation)));
            };
        }

        private static final Map<String, String[]> ERRORS = new TreeMap<>(Map.of(
                "400", new String[]{"BadRequest", "The request is malformed or fails validation; `fields` names the offending fields, never their values."},
                "401", new String[]{"Unauthenticated", "No valid access token (missing, expired, forged, or the account is no longer active)."},
                "403", new String[]{"Forbidden", "The caller's role does not allow this (decided by the use case)."},
                "404", new String[]{"NotFound", "Not found - also what an id of another association gets."},
                "409", new String[]{"Conflict", "A conflict: duplicate, last administrator, or a concurrent change that survived the retries."},
                "422", new String[]{"RuleViolation", "A business rule refuses it; `message` says which."},
                "429", new String[]{"TooManyRequests", "Too many requests; see `Retry-After`."}));

        private static void describe(String path, PathItem.HttpMethod method, Operation operation) {
            boolean isPublic = PublicRoutes.permits(method.name(), path);
            operation.setSecurity(isPublic ? List.of() : List.of(new SecurityRequirement().addList(BEARER)));
            operation.setOperationId(operationId(operation));
            ApiResponses responses = operation.getResponses();
            boolean writes = method != PathItem.HttpMethod.GET;
            error(responses, "400");
            if (!isPublic) {
                error(responses, "401");
                error(responses, "403");
            }
            if (path.contains("{") || !isPublic) {
                error(responses, "404");
            }
            if (writes) {
                error(responses, "409");
                error(responses, "422");
            }
            error(responses, "429");
            if (path.equals("/api/v1/subscriptions/export")) {
                responses.get("200").setContent(new Content().addMediaType("text/csv", new MediaType().schema(new StringSchema())));
            }
        }

        /** {@code venue-controller} + {@code create_1} becomes {@code venueCreate}: stable, and unique because method names are per controller. */
        private static String operationId(Operation operation) {
            String tag = operation.getTags().get(0).replace("-controller", "");
            StringBuilder name = new StringBuilder();
            for (String part : tag.split("-")) {
                name.append(name.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
            }
            String method = operation.getOperationId().replaceAll("_\\d+$", "");
            return name.toString() + Character.toUpperCase(method.charAt(0)) + method.substring(1);
        }

        private static void error(ApiResponses responses, String code) {
            if (responses.get(code) == null) {
                responses.addApiResponse(code, new ApiResponse().$ref("#/components/responses/" + ERRORS.get(code)[0]));
            }
        }

        static void addSharedResponses(Components components) {
            Schema<?> ref = new Schema<>().$ref("#/components/schemas/ApiError");
            ERRORS.values().forEach(entry -> components.addResponses(entry[0], new ApiResponse().description(entry[1])
                    .content(new Content().addMediaType("application/json", new MediaType().schema(ref)))));
        }
    }

    private String generated() throws Exception {
        Tenant tenant = registerTenant();
        MvcResult result = expect(200, HttpMethod.GET, "/v3/api-docs", tenant.admin().token(), null);
        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).enable(SerializationFeature.INDENT_OUTPUT).build();
        Object tree = mapper.readValue(result.getResponse().getContentAsString(StandardCharsets.UTF_8), Object.class);
        return mapper.writeValueAsString(tree) + "\n";
    }

    @Test
    void theCommittedDescriptionIsWhatTheControllersGenerate() throws Exception {
        // Arrange
        String generated = generated();
        if (System.getProperty("openapi.update") != null) {
            Files.createDirectories(COMMITTED.getParent());
            Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
        }

        // Act
        String committed = Files.exists(COMMITTED) ? Files.readString(COMMITTED, StandardCharsets.UTF_8) : "";

        // Assert
        assertThat(committed).as("docs/api/openapi.json is out of date: run ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true and commit it")
                .isEqualTo(generated);
    }

    @Test
    void everyOperationSaysWhoMayCallItAndHowItFails() throws Exception {
        // Arrange
        String generated = generated();
        JsonMapper mapper = JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Map<String, Object>>> paths = (Map<String, Map<String, Map<String, Object>>>) ((Map<String, Object>) mapper.readValue(generated, Object.class)).get("paths");

        // Act
        List<String> problems = new ArrayList<>();
        Set<String> operations = new TreeSet<>();
        for (Map.Entry<String, Map<String, Map<String, Object>>> path : paths.entrySet()) {
            for (Map.Entry<String, Map<String, Object>> operation : path.getValue().entrySet()) {
                String key = operation.getKey().toUpperCase() + " " + path.getKey();
                operations.add(key);
                boolean isPublic = PublicRoutes.permits(operation.getKey().toUpperCase(), path.getKey());
                Object security = operation.getValue().get("security");
                if (isPublic != (security instanceof List<?> list && list.isEmpty())) {
                    problems.add(key + ": security " + security + " disagrees with PublicRoutes");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> responses = (Map<String, Object>) operation.getValue().get("responses");
                if (responses.keySet().stream().noneMatch(code -> code.startsWith("2"))) {
                    problems.add(key + ": no success response");
                }
                if (!responses.containsKey("400") || !responses.containsKey("429")) {
                    problems.add(key + ": missing the shared error responses");
                }
            }
        }

        // Assert
        assertThat(problems).isEmpty();
        assertThat(operations).contains("POST /api/v1/sessions/{sessionId}/bookings", "GET /api/v1/public/associations/{shortName}",
                "GET /api/v1/subscriptions/export", "POST /api/v1/auth/login");
        assertThat(operations).noneMatch(operation -> operation.contains("/error")).noneMatch(operation -> operation.contains("api-docs"));
    }
}
