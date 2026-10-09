package com.regivolley.api.integration;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.result.JoinRequestPurgeReport;
import com.regivolley.api.application.usecase.PurgeStalePendingJoinRequestsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RGPD data minimisation (threat model S1): a join request undecided for 30 days is erased, one younger is kept, an approved one is untouched. */
class StaleJoinRequestPurgeIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    private PurgeStalePendingJoinRequestsUseCase purge;

    @Test
    void aRequestUndecidedForThirtyDaysIsAnonymisedAndTheRestIsLeftAlone() throws Exception {
        // Arrange
        Tenant tenant = registerTenant();
        String staleEmail = "stale." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody("Stale Person", staleEmail));
        String staleId = read(expect(200, HttpMethod.GET, "/api/v1/join-requests", tenant.admin().token(), null), "$[0].id");
        Person approved = joinAndActivate(tenant, "Approved Person");
        clock.advance(Duration.ofDays(20));
        String freshEmail = "fresh." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody("Fresh Person", freshEmail));
        clock.advance(Duration.ofDays(11));
        String token = tokenAfterTimeTravel(tenant);

        // Act
        JoinRequestPurgeReport report = purge.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        assertThat(report.requestsAnonymised()).isGreaterThanOrEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap("select name, email, phone, status, consent_policy_version from join_requests where id = ?::uuid", staleId);
        assertThat(row.get("name")).isEqualTo("Anonymised member");
        assertThat((String) row.get("email")).endsWith("@anonymised.invalid");
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("status")).isEqualTo("REJECTED");
        assertThat(row.get("consent_policy_version")).isEqualTo("2026-01");
        List<String> stillPending = read(expect(200, HttpMethod.GET, "/api/v1/join-requests", token, null), "$[*].email");
        assertThat(stillPending).containsExactly(freshEmail);
        assertThat(jdbc.queryForObject("select status from join_requests where association_id = ?::uuid and email = ?", String.class, tenant.associationId(), approved.email()))
                .isEqualTo("APPROVED");
        assertThat(purge.execute(new PurgeStalePendingJoinRequestsCommand()).requestsAnonymised()).as("idempotent").isZero();
    }

    /** The access token lives 10 minutes; after moving the clock a month the administrator signs in again. */
    private String tokenAfterTimeTravel(Tenant tenant) throws Exception {
        return read(expect(200, HttpMethod.POST, "/api/v1/auth/login", null, json("email", tenant.admin().email(), "password", PASSWORD)), "$.accessToken");
    }
}
