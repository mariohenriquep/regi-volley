package com.regivolley.api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The administrator's read endpoints (issue #49) over the whole stack, on a world shaped like the dev seed: a founder who is
 * administrator and coach, a coach, a member with a part-paid pack and a booking, three levels, a venue, a group and a plan.
 * Each list is read before and after the administrator changes something, so the screens' view is proved to follow the data.
 */
class AdminReadEndpointsIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    void theMemberListIsByNameFilteredByStatusAndSearchedByNameOrEmail() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String founder = "Founder " + w.tenant().shortName();

        // Act
        MvcResult all = expect(200, HttpMethod.GET, "/api/v1/members", w.adminToken(), null);
        expect(200, HttpMethod.POST, "/api/v1/members/" + w.spare().memberId() + "/deactivation", w.adminToken(), null);
        MvcResult inactive = expect(200, HttpMethod.GET, "/api/v1/members?status=INACTIVE", w.adminToken(), null);
        MvcResult active = expect(200, HttpMethod.GET, "/api/v1/members?status=ACTIVE", w.adminToken(), null);
        MvcResult byName = expect(200, HttpMethod.GET, "/api/v1/members?q= RITA ", w.adminToken(), null);
        MvcResult byEmail = expect(200, HttpMethod.GET, "/api/v1/members?q=" + w.member().email().substring(0, 14).toUpperCase(), w.adminToken(), null);
        MvcResult wildcard = expect(200, HttpMethod.GET, "/api/v1/members?q=%", w.adminToken(), null);

        // Assert
        assertThat((List<String>) read(all, "$.members[*].name")).containsExactly(founder, "Paulo Dias", "Rita Costa");
        assertThat((Boolean) read(all, "$.truncated")).isFalse();
        assertThat((List<String>) read(all, "$.members[*].levelName")).containsOnly("Beginner");
        assertThat((List<Boolean>) read(all, "$.members[*].accountActivated")).containsOnly(true);
        assertThat((List<String>) read(all, "$.members[*].roles[*]")).contains("ADMIN", "COACH", "MEMBER");
        assertThat((List<String>) read(inactive, "$.members[*].memberId")).containsExactly(w.spare().memberId());
        assertThat((List<String>) read(active, "$.members[*].name")).containsExactly(founder, "Rita Costa");
        assertThat((List<String>) read(byName, "$.members[*].memberId")).containsExactly(w.member().memberId());
        assertThat((List<String>) read(byEmail, "$.members[*].memberId")).containsExactly(w.member().memberId());
        assertThat((List<String>) read(wildcard, "$.members[*].memberId")).isEmpty();
    }

    @Test
    void aMemberWhoNeverUsedTheirActivationLinkIsNotActivated() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String pendingMember = read(expect(200, HttpMethod.POST, "/api/v1/join-requests/" + w.approvableRequestId() + "/approval", w.adminToken(), null), "$.memberId");

        // Act
        MvcResult list = expect(200, HttpMethod.GET, "/api/v1/members?q=Ines", w.adminToken(), null);
        MvcResult detail = expect(200, HttpMethod.GET, "/api/v1/members/" + pendingMember, w.adminToken(), null);

        // Assert
        assertThat((List<Boolean>) read(list, "$.members[*].accountActivated")).containsExactly(false);
        assertThat((Boolean) read(detail, "$.accountActivated")).isFalse();
        assertThat((String) read(detail, "$.name")).isEqualTo("Ines Lopes");
    }

    @Test
    void theMemberDetailHasTheLevelHistoryWithNamesAndTheSubscriptionsWithAmountsAndCredits() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.PUT, "/api/v1/members/" + w.member().memberId() + "/level", w.adminToken(), "{\"levelId\":\"" + w.secondLevelId() + "\"}");

        // Act
        MvcResult detail = expect(200, HttpMethod.GET, "/api/v1/members/" + w.member().memberId(), w.adminToken(), null);

        // Assert
        assertThat((String) read(detail, "$.name")).isEqualTo("Rita Costa");
        assertThat((Boolean) read(detail, "$.anonymised")).isFalse();
        assertThat((String) read(detail, "$.email")).isEqualTo(w.member().email());
        assertThat((String) read(detail, "$.levelId")).isEqualTo(w.secondLevelId());
        assertThat((String) read(detail, "$.levelName")).isEqualTo("Intermediate");
        assertThat((List<String>) read(detail, "$.levelChanges[*].fromLevelName")).containsExactly("Beginner");
        assertThat((List<String>) read(detail, "$.levelChanges[*].toLevelName")).containsExactly("Intermediate");
        assertThat((List<String>) read(detail, "$.levelChanges[*].changedBy")).containsExactly(w.adminId());
        assertThat((List<String>) read(detail, "$.levelChanges[*].changedByName")).containsExactly("Founder " + w.tenant().shortName());
        assertThat((List<String>) read(detail, "$.subscriptions[*].subscriptionId")).containsExactly(w.subscriptionId());
        assertThat((String) read(detail, "$.subscriptions[0].planName")).isEqualTo("Ten sessions");
        assertThat((String) read(detail, "$.subscriptions[0].type")).isEqualTo("PACK");
        assertThat((String) read(detail, "$.subscriptions[0].paymentStatus")).isEqualTo("PENDING");
        assertThat((Integer) read(detail, "$.subscriptions[0].priceCents")).isEqualTo(4500);
        assertThat((Integer) read(detail, "$.subscriptions[0].paidCents")).isEqualTo(1500);
        assertThat((Integer) read(detail, "$.subscriptions[0].outstandingCents")).isEqualTo(3000);
        assertThat((Integer) read(detail, "$.subscriptions[0].creditsUsed")).isEqualTo(1);
        assertThat((Integer) read(detail, "$.subscriptions[0].creditsLeft")).isEqualTo(9);
        assertThat((Boolean) read(detail, "$.subscriptionsTruncated")).isFalse();
    }

    @Test
    void aMemberWithoutHistoryOrSubscriptionsHasEmptyLists() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult detail = expect(200, HttpMethod.GET, "/api/v1/members/" + w.spare().memberId(), w.adminToken(), null);

        // Assert
        assertThat((List<Object>) read(detail, "$.levelChanges")).isEmpty();
        assertThat((List<Object>) read(detail, "$.subscriptions")).isEmpty();
        assertThat((List<String>) read(detail, "$.roles")).containsExactly("MEMBER", "COACH");
    }

    @Test
    void theSubscriptionsPaymentsFollowARecordedPaymentAndItsReversal() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        String path = "/api/v1/subscriptions/" + w.subscriptionId() + "/payments";
        MvcResult before = expect(200, HttpMethod.GET, path, w.adminToken(), null);
        clock.set(START.plusSeconds(60));
        expect(201, HttpMethod.POST, path, w.adminToken(), "{\"amountCents\":3000,\"paidOn\":\"2026-10-12\",\"method\":\"TRANSFER\"}");
        MvcResult settled = expect(200, HttpMethod.GET, path, w.adminToken(), null);

        // Act
        clock.set(START.plusSeconds(120));
        expect(201, HttpMethod.POST, "/api/v1/payments/" + w.paymentId() + "/reversal", w.adminToken(), null);
        MvcResult reversed = expect(200, HttpMethod.GET, path, w.adminToken(), null);

        // Assert
        assertThat((String) read(before, "$.subscriptionId")).isEqualTo(w.subscriptionId());
        assertThat((Integer) read(before, "$.paidCents")).isEqualTo(1500);
        assertThat((Integer) read(before, "$.outstandingCents")).isEqualTo(3000);
        assertThat((List<String>) read(before, "$.payments[*].id")).containsExactly(w.paymentId());
        assertThat((List<String>) read(before, "$.payments[*].recordedByName")).containsExactly("Founder " + w.tenant().shortName());
        assertThat((String) read(settled, "$.paymentStatus")).isEqualTo("PAID");
        assertThat((Integer) read(settled, "$.outstandingCents")).isZero();
        assertThat((List<String>) read(settled, "$.payments[*].method")).containsExactly("CASH", "TRANSFER");
        assertThat((String) read(reversed, "$.paymentStatus")).isEqualTo("PENDING");
        assertThat((Integer) read(reversed, "$.paidCents")).isEqualTo(3000);
        assertThat((Integer) read(reversed, "$.outstandingCents")).isEqualTo(1500);
        assertThat((List<Object>) read(reversed, "$.payments")).hasSize(3);
        assertThat((List<String>) read(reversed, "$.payments[?(@.reversalOf)].reversalOf")).containsExactly(w.paymentId());
    }

    @Test
    void theLevelsVenuesGroupsAndPlansAreTheAssociationsOwnWithNamesResolved() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult levels = expect(200, HttpMethod.GET, "/api/v1/levels", w.adminToken(), null);
        MvcResult venues = expect(200, HttpMethod.GET, "/api/v1/venues", w.adminToken(), null);
        MvcResult groups = expect(200, HttpMethod.GET, "/api/v1/training-groups", w.adminToken(), null);
        MvcResult plans = expect(200, HttpMethod.GET, "/api/v1/plans", w.adminToken(), null);

        // Assert
        assertThat((List<String>) read(levels, "$.levels[*].name")).containsExactly("Beginner", "Intermediate", "Advanced");
        assertThat((List<String>) read(levels, "$.levels[*].id")).containsExactly(w.entryLevelId(), w.secondLevelId(), w.thirdLevelId());
        assertThat((String) read(levels, "$.entryLevelId")).isEqualTo(w.entryLevelId());
        assertThat((List<String>) read(venues, "$[*].name")).containsExactly("Pavilion");
        assertThat((List<Boolean>) read(venues, "$[*].inUseByActiveGroup")).containsExactly(true);
        assertThat((List<Integer>) read(venues, "$[*].courts")).containsExactly(2);
        assertThat((List<String>) read(groups, "$[*].id")).containsExactly(w.groupId());
        assertThat((List<String>) read(groups, "$[*].acceptedLevels[*].name")).containsExactly("Beginner");
        assertThat((String) read(groups, "$[0].venue.id")).isEqualTo(w.venueId());
        assertThat((String) read(groups, "$[0].venue.name")).isEqualTo("Pavilion");
        assertThat((String) read(groups, "$[0].coach.id")).isEqualTo(w.adminId());
        assertThat((String) read(groups, "$[0].coach.name")).isEqualTo("Founder " + w.tenant().shortName());
        assertThat((String) read(groups, "$[0].schedule[0].startTime")).isEqualTo("20:00");
        assertThat((String) read(groups, "$[0].schedule[0].dayOfWeek")).isEqualTo("WEDNESDAY");
        assertThat((Integer) read(groups, "$[0].schedule[0].durationMinutes")).isEqualTo(90);
        assertThat((Integer) read(groups, "$[0].capacity")).isEqualTo(12);
        assertThat((String) read(groups, "$[0].status")).isEqualTo("ACTIVE");
        assertThat((List<String>) read(plans, "$[*].name")).containsExactly("Ten sessions");
        assertThat((String) read(plans, "$[0].type")).isEqualTo("PACK");
        assertThat((Integer) read(plans, "$[0].credits")).isEqualTo(10);
        assertThat((Integer) read(plans, "$[0].priceCents")).isEqualTo(4500);
        assertThat((Integer) read(plans, "$[0].validityDays")).isEqualTo(90);
        assertThat((List<String>) read(plans, "$[0].allowedLevels[*].name")).containsExactly("Beginner");
    }

    @Test
    void archivingTheGroupFreesTheVenueAndHidesTheGroupUnlessArchivedAreAskedFor() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();
        expect(200, HttpMethod.POST, "/api/v1/training-groups/" + w.groupId() + "/archival", w.adminToken(), null);

        // Act
        MvcResult venues = expect(200, HttpMethod.GET, "/api/v1/venues", w.adminToken(), null);
        MvcResult hidden = expect(200, HttpMethod.GET, "/api/v1/training-groups", w.adminToken(), null);
        MvcResult shown = expect(200, HttpMethod.GET, "/api/v1/training-groups?includeArchived=true", w.adminToken(), null);

        // Assert
        assertThat((List<Boolean>) read(venues, "$[*].inUseByActiveGroup")).containsExactly(false);
        assertThat(body(hidden)).isEqualTo("[]");
        assertThat((List<String>) read(shown, "$[*].status")).containsExactly("ARCHIVED");
        expect(204, HttpMethod.DELETE, "/api/v1/venues/" + w.venueId(), w.adminToken(), null);
    }

    @Test
    void aTextOverTheLimitIsAValidationErrorNotASearch() throws Exception {
        // Arrange
        TenantWorld w = seedWorld();

        // Act
        MvcResult result = send(HttpMethod.GET, "/api/v1/members?q=" + "x".repeat(101), w.adminToken());

        // Assert
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(body(result)).contains("\"fields\"").contains("\"q\"");
    }
}
