package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Threat model P1, P3, P4 and D-9/D-10/D-14 against the real application: what a stranger can and cannot learn from the three public
 * endpoints, and how often they may ask.
 */
class PublicEndpointsIntegrationTest extends AbstractApiIntegrationTest {

    private static final String LOCATION = "location";

    /** Status, headers and body of a response, minus what is allowed to differ (request id, date), comparable across requests. */
    private static Map<String, Object> shape(MvcResult result) throws Exception {
        Map<String, Object> shape = new TreeMap<>();
        shape.put("status", result.getResponse().getStatus());
        shape.put("body", result.getResponse().getContentAsString().replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"-\""));
        for (String name : result.getResponse().getHeaderNames()) {
            if (!name.equalsIgnoreCase("X-Request-Id") && !name.equalsIgnoreCase("Date") && !name.equalsIgnoreCase(LOCATION)) {
                shape.put("header:" + name.toLowerCase(), result.getResponse().getHeaders(name));
            }
        }
        return shape;
    }

    private MvcResult postFrom(String address, String path, String body) throws Exception {
        return mockMvc.perform(post(path).with(request -> {
            request.setRemoteAddr(address);
            return request;
        }).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    // ---- registering an association: nothing to enumerate ------------------------------------------------------

    @Test
    void registeringAnswersIdenticallyForANewAKnownAndAnUnactivatedFounderEmail() throws Exception {
        // Arrange - one email already belongs to an activated account, one has an account that was never activated
        Tenant existing = registerTenant();
        String knownEmail = existing.admin().email();
        String unactivatedEmail = "pending." + unique() + "@example.com";
        expect(201, HttpMethod.POST, "/api/v1/public/associations", null, registerBody("first-" + unique(), unactivatedEmail));

        // Act
        MvcResult brandNew = send(HttpMethod.POST, "/api/v1/public/associations", null, registerBody("fresh-" + unique(), "new." + unique() + "@example.com"));
        MvcResult known = send(HttpMethod.POST, "/api/v1/public/associations", null, registerBody("known-" + unique(), knownEmail));
        MvcResult unactivated = send(HttpMethod.POST, "/api/v1/public/associations", null, registerBody("unact-" + unique(), unactivatedEmail));

        // Assert - same status, headers and body (the short name apart, which the visitor typed)
        assertThat(brandNew.getResponse().getStatus()).isEqualTo(201);
        assertThat(stripName(shape(known), "known-")).isEqualTo(stripName(shape(brandNew), "fresh-"));
        assertThat(stripName(shape(unactivated), "unact-")).isEqualTo(stripName(shape(brandNew), "fresh-"));
        assertThat(body(known)).doesNotContain(knownEmail).doesNotContain(existing.associationId());
    }

    private static Map<String, Object> stripName(Map<String, Object> shape, String prefix) {
        Map<String, Object> copy = new TreeMap<>(shape);
        copy.put("body", ((String) copy.get("body")).replaceAll(prefix + "[0-9a-f]{8}", "NAME"));
        return copy;
    }

    @Test
    void aTakenShortNameIs409AndPublicInformationOnly() throws Exception {
        // Arrange
        Tenant existing = registerTenant();

        // Act
        MvcResult taken = send(HttpMethod.POST, "/api/v1/public/associations", null, registerBody(existing.shortName(), "other." + unique() + "@example.com"));

        // Assert
        assertThat(taken.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(taken)).doesNotContain(existing.associationId()).doesNotContain(existing.admin().email());
    }

    @Test
    void registeringIsLimitedToThreeAnHourPerAddress() throws Exception {
        // Arrange
        String address = "198.51.100." + (10 + Integer.parseInt(unique().substring(0, 2), 16) % 200);

        // Act
        int[] statuses = new int[4];
        for (int i = 0; i < 4; i++) {
            statuses[i] = postFrom(address, "/api/v1/public/associations", registerBody("lim-" + unique(), "lim." + unique() + "@example.com")).getResponse().getStatus();
        }

        // Assert
        assertThat(statuses).containsExactly(201, 201, 201, 429);
    }

    // ---- joining: one answer for every outcome ------------------------------------------------------------------

    @Test
    void aJoinRequestAnswersByteForByteTheSameForANewAddressAMemberAndAPendingRequest() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        Person member = joinAndActivate(tenant, "Rita Costa");
        String pendingEmail = "pending." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody("Pending Person", pendingEmail));
        String path = "/api/v1/public/associations/" + tenant.shortName() + "/join-requests";

        // Act
        MvcResult created = send(HttpMethod.POST, path, null, joinBody("New Person", "new." + unique() + "@example.com"));
        MvcResult alreadyMember = send(HttpMethod.POST, path, null, joinBody("Rita Again", member.email()));
        MvcResult alreadyPending = send(HttpMethod.POST, path, null, joinBody("Pending Again", pendingEmail));
        MvcResult founder = send(HttpMethod.POST, path, null, joinBody("Founder Again", tenant.admin().email()));

        // Assert
        assertThat(shape(created).get("status")).isEqualTo(202);
        assertThat(body(created)).isEqualTo("{\"status\":\"RECEIVED\"}");
        assertThat(shape(alreadyMember)).isEqualTo(shape(created));
        assertThat(shape(alreadyPending)).isEqualTo(shape(created));
        assertThat(shape(founder)).isEqualTo(shape(created));
        assertThat(jdbc.queryForObject("select count(*) from join_requests where association_id = ?::uuid and status = 'PENDING'", Integer.class, tenant.associationId()))
                .as("only the genuinely new address left a request").isEqualTo(2);
    }

    @Test
    void everyRefusalOfTheInputIsTheSameForANewAMemberAndAPendingAddress() throws Exception {
        // Arrange - threat model P1: a refusal that depended on stored data would tell a stranger which addresses are known
        Tenant tenant = registerTenant();
        Person member = joinAndActivate(tenant, "Rita Costa");
        String pendingEmail = "pending." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody("Pending Person", pendingEmail));
        String path = "/api/v1/public/associations/" + tenant.shortName() + "/join-requests";
        String freshEmail = "fresh." + unique() + "@example.com";

        // Act + Assert - no consent
        MvcResult noConsentNew = send(HttpMethod.POST, path, null, joinBody("New Person", freshEmail).replace("\"consentAccepted\":true", "\"consentAccepted\":false"));
        MvcResult noConsentMember = send(HttpMethod.POST, path, null, joinBody("Rita Again", member.email()).replace("\"consentAccepted\":true", "\"consentAccepted\":false"));
        MvcResult noConsentPending = send(HttpMethod.POST, path, null, joinBody("Pending Again", pendingEmail).replace("\"consentAccepted\":true", "\"consentAccepted\":false"));
        assertThat(noConsentNew.getResponse().getStatus()).isEqualTo(422);
        assertThat(shape(noConsentMember)).isEqualTo(shape(noConsentNew));
        assertThat(shape(noConsentPending)).isEqualTo(shape(noConsentNew));

        // Act + Assert - a blank policy version (stopped by the request validation, 400) and a phone number that is not one (the domain, 422)
        for (String broken : List.of("\"policyVersion\":\"2026-01\"", "\"phone\":\"912345679\"")) {
            String replacement = broken.startsWith("\"policy") ? "\"policyVersion\":\" \"" : "\"phone\":\"12\"";
            int refused = broken.startsWith("\"policy") ? 400 : 422;
            MvcResult newAddress = send(HttpMethod.POST, path, null, joinBody("New Person", freshEmail).replace(broken, replacement));
            MvcResult memberAddress = send(HttpMethod.POST, path, null, joinBody("Rita Again", member.email()).replace(broken, replacement));
            MvcResult pendingAddress = send(HttpMethod.POST, path, null, joinBody("Pending Again", pendingEmail).replace(broken, replacement));
            assertThat(newAddress.getResponse().getStatus()).isEqualTo(refused);
            assertThat(shape(memberAddress)).isEqualTo(shape(newAddress));
            assertThat(shape(pendingAddress)).isEqualTo(shape(newAddress));
        }
        assertThat(jdbc.queryForObject("select count(*) from join_requests where association_id = ?::uuid", Integer.class, tenant.associationId()))
                .as("no refused request left a row").isEqualTo(2);
    }

    @Test
    void registeringRefusesAnInvalidInputBeforeAndWhateverTheShortNameOrTheEmailWas() throws Exception {
        // Arrange
        Tenant existing = registerTenant();
        String noConsent = registerBody(existing.shortName(), existing.admin().email()).replace("\"consentAccepted\":true", "\"consentAccepted\":false");
        String freshNoConsent = registerBody("fresh-" + unique(), "new." + unique() + "@example.com").replace("\"consentAccepted\":true", "\"consentAccepted\":false");

        // Act
        MvcResult takenName = send(HttpMethod.POST, "/api/v1/public/associations", null, noConsent);
        MvcResult freshName = send(HttpMethod.POST, "/api/v1/public/associations", null, freshNoConsent);

        // Assert - a taken short name does not turn a missing consent into a 409 (nor the other way round)
        assertThat(takenName.getResponse().getStatus()).isEqualTo(422);
        assertThat(shape(takenName)).isEqualTo(shape(freshName));
    }

    @Test
    void aTextThatCannotBeAShortNameIs404LikeAnUnknownOneOnTheJoinRouteAndThePage() throws Exception {
        // Arrange
        String unknown = "no-such-club-" + unique();

        // Act
        MvcResult malformedPage = send(HttpMethod.GET, "/api/v1/public/associations/Not_A_Short_Name", null);
        MvcResult unknownPage = send(HttpMethod.GET, "/api/v1/public/associations/" + unknown, null);
        MvcResult malformedJoin = send(HttpMethod.POST, "/api/v1/public/associations/Not_A_Short_Name/join-requests", null, joinBody("Rita Costa", "rita." + unique() + "@example.com"));
        MvcResult unknownJoin = send(HttpMethod.POST, "/api/v1/public/associations/" + unknown + "/join-requests", null, joinBody("Rita Costa", "rita." + unique() + "@example.com"));

        // Assert
        assertThat(malformedPage.getResponse().getStatus()).isEqualTo(404);
        assertThat(shape(malformedPage)).isEqualTo(shape(unknownPage));
        assertThat(malformedJoin.getResponse().getStatus()).isEqualTo(404);
        assertThat(shape(malformedJoin)).isEqualTo(shape(unknownJoin));
    }

    @Test
    void anUnknownAssociationIs404ForTheJoinRequestAndThePageAlike() throws Exception {
        // Arrange
        String missing = "no-such-club-" + unique();

        // Act
        MvcResult join = send(HttpMethod.POST, "/api/v1/public/associations/" + missing + "/join-requests", null, joinBody("Rita Costa", "rita." + unique() + "@example.com"));
        MvcResult page = send(HttpMethod.GET, "/api/v1/public/associations/" + missing, null);

        // Assert
        assertThat(join.getResponse().getStatus()).isEqualTo(404);
        assertThat(page.getResponse().getStatus()).isEqualTo(404);
        assertThat(shape(join).get("body")).isEqualTo(shape(page).get("body"));
    }

    @Test
    void theSameAddressMayAskAnAssociationOnlyThreeTimesADayWhateverTheAnswerWas() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        Tenant other = registerTenant();
        String path = "/api/v1/public/associations/" + tenant.shortName() + "/join-requests";
        String email = "eager." + unique() + "@example.com";

        // Act
        MvcResult first = send(HttpMethod.POST, path, null, joinBody("Eager One", email));
        MvcResult second = send(HttpMethod.POST, path, null, joinBody("Eager Two", email.toUpperCase()));
        MvcResult third = send(HttpMethod.POST, path, null, joinBody("Eager Three", email));
        MvcResult fourth = send(HttpMethod.POST, path, null, joinBody("Eager Four", email));
        MvcResult anotherAddress = send(HttpMethod.POST, path, null, joinBody("Someone Else", "else." + unique() + "@example.com"));
        MvcResult anotherAssociation = send(HttpMethod.POST, "/api/v1/public/associations/" + other.shortName() + "/join-requests", null, joinBody("Eager Five", email));
        clock.advance(Duration.ofDays(1).plusMinutes(1));
        MvcResult nextDay = send(HttpMethod.POST, path, null, joinBody("Eager Six", email));

        // Assert - the 2nd and 3rd are "pending already" yet look the same as the 1st; the 4th is refused with Retry-After
        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(shape(second)).isEqualTo(shape(first));
        assertThat(shape(third)).isEqualTo(shape(first));
        assertThat(fourth.getResponse().getStatus()).isEqualTo(429);
        assertThat(fourth.getResponse().getHeader("Retry-After")).isNotNull();
        assertThat(anotherAddress.getResponse().getStatus()).isEqualTo(202);
        assertThat(anotherAssociation.getResponse().getStatus()).isEqualTo(202);
        assertThat(nextDay.getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void joiningIsLimitedToFiveAnHourPerAddress() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String address = "203.0.113." + (10 + Integer.parseInt(unique().substring(0, 2), 16) % 200);
        String path = "/api/v1/public/associations/" + tenant.shortName() + "/join-requests";

        // Act
        int[] statuses = new int[6];
        for (int i = 0; i < 6; i++) {
            statuses[i] = postFrom(address, path, joinBody("Visitor " + i, "visitor" + i + "." + unique() + "@example.com")).getResponse().getStatus();
        }

        // Assert
        assertThat(statuses).containsExactly(202, 202, 202, 202, 202, 429);
    }

    // ---- the public page ----------------------------------------------------------------------------------------

    @Test
    void thePublicPageShowsOnlyThatAssociationsPublicData() throws Exception {
        // Arrange
        TenantWorld a = seedWorld();
        TenantWorld b = seedWorld();

        // Act
        MvcResult page = expect(200, HttpMethod.GET, "/api/v1/public/associations/" + a.tenant().shortName(), null, null);

        // Assert
        String text = body(page);
        assertThat(text).contains(a.tenant().shortName()).contains("Wednesday").contains("Pavilion")
                .contains("info@" + a.tenant().shortName() + ".example");
        assertThat(text).doesNotContain(b.tenant().shortName()).doesNotContain(b.groupId()).doesNotContain(b.venueId())
                .doesNotContain(a.groupId()).doesNotContain(a.venueId()).doesNotContain(a.adminId())
                .doesNotContain(a.member().email()).doesNotContain(a.tenant().admin().email()).doesNotContain("Rita").doesNotContain("Paulo");
    }
}
