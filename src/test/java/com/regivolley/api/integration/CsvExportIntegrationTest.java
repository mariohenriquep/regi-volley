package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-22 with the real database: the overdue list as a CSV download. Member names are typed by strangers (a join request), so the
 * file must not let a spreadsheet run them as formulas, and it must hold nothing from another association.
 */
class CsvExportIntegrationTest extends AbstractApiIntegrationTest {

    private String planOf(Tenant tenant) throws Exception {
        return read(expect(201, HttpMethod.POST, "/api/v1/plans", tenant.admin().token(),
                "{\"name\":\"Monthly\",\"type\":\"MONTHLY_UNLIMITED\",\"priceCents\":3000}"), "$.id");
    }

    /** A member with the given name (JSON-escaped by the caller) whose subscription is marked overdue; returns their subscription id. */
    private String overdueMember(Tenant tenant, String planId, String jsonName) throws Exception {
        String email = "member." + unique() + "@example.com";
        expect(202, HttpMethod.POST, "/api/v1/public/associations/" + tenant.shortName() + "/join-requests", null, joinBody(jsonName, email));
        MvcResult pending = expect(200, HttpMethod.GET, "/api/v1/join-requests", tenant.admin().token(), null);
        List<String> ids = read(pending, "$[?(@.email == '" + email + "')].id");
        String memberId = read(expect(200, HttpMethod.POST, "/api/v1/join-requests/" + ids.get(0) + "/approval", tenant.admin().token(), null), "$.memberId");
        String subscriptionId = read(expect(201, HttpMethod.POST, "/api/v1/members/" + memberId + "/subscriptions", tenant.admin().token(),
                "{\"planId\":\"" + planId + "\",\"startDate\":\"2026-10-12\"}"), "$.id");
        expect(200, HttpMethod.POST, "/api/v1/subscriptions/" + subscriptionId + "/overdue-marking", tenant.admin().token(), null);
        return subscriptionId;
    }

    @Test
    void theCsvDefusesFormulasQuotesTextAndHoldsNothingFromAnotherAssociation() throws Exception {
        // Arrange
        Tenant a = registerTenant();
        Tenant b = registerTenant();
        String planA = planOf(a);
        String planB = planOf(b);
        overdueMember(a, planA, "=1+1");
        overdueMember(a, planA, "+cmd|' /C calc'!A0");
        overdueMember(a, planA, "-2+3");
        overdueMember(a, planA, "@SUM(1+1)");
        overdueMember(a, planA, "Silva, \\\"Ana\\\"");
        overdueMember(a, planA, "Plain Person");
        String foreignSubscription = overdueMember(b, planB, "Bruno Elsewhere");

        // Act
        MvcResult result = expect(200, HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=OVERDUE", a.admin().token(), null);

        // Assert
        assertThat(result.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"subscriptions-overdue.csv\"");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>(List.of(csv.split("\r\n")));
        assertThat(lines.get(0)).isEqualTo("﻿subscription_id,member_id,member_name,plan_type,start_date,end_date,payment_status,price_eur");
        assertThat(lines).hasSize(7);
        assertThat(csv).contains(",'=1+1,").contains(",'+cmd|' /C calc'!A0,").contains(",'-2+3,").contains(",'@SUM(1+1),")
                .contains(",\"Silva, \"\"Ana\"\"\",").contains(",Plain Person,");
        assertThat(lines.stream().skip(1)).allSatisfy(line -> assertThat(line.split(",", 4)[2]).doesNotStartWith("=").doesNotStartWith("+")
                .doesNotStartWith("-").doesNotStartWith("@"));
        assertThat(csv).doesNotContain("Bruno Elsewhere").doesNotContain(foreignSubscription).doesNotContain(b.admin().memberId());
        assertThat(csv).contains(",OVERDUE,30.00");
    }

    @Test
    void theJsonListAndTheCsvAgreeAndNeitherCrossesTheTenantLine() throws Exception {
        // Arrange
        Tenant a = registerTenant();
        Tenant b = registerTenant();
        String sa = overdueMember(a, planOf(a), "Ana Silva");
        String sb = overdueMember(b, planOf(b), "Bruno Costa");

        // Act
        MvcResult listA = expect(200, HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=OVERDUE", a.admin().token(), null);
        MvcResult csvB = expect(200, HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=OVERDUE", b.admin().token(), null);
        MvcResult pendingA = expect(200, HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PENDING", a.admin().token(), null);

        // Assert
        assertThat(this.<List<String>>read(listA, "$[*].subscriptionId")).containsExactly(sa);
        assertThat(csvB.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(sb).doesNotContain(sa);
        assertThat(this.<List<?>>read(pendingA, "$")).isEmpty();
    }

    @Test
    void aStatusThatIsNotOneOfTheThreeIs400AndAnEmptyListIsJustTheHeader() throws Exception {
        // Arrange
        Tenant a = registerTenant();

        // Act
        MvcResult bad = send(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=LATE", a.admin().token());
        MvcResult empty = expect(200, HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PAID", a.admin().token(), null);

        // Assert
        assertThat(bad.getResponse().getStatus()).isEqualTo(400);
        assertThat(empty.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo("﻿subscription_id,member_id,member_name,plan_type,start_date,end_date,payment_status,price_eur\r\n");
    }
}
