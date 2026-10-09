package com.regivolley.api.integration;

import java.util.UUID;

/**
 * Everything a tenant owns that an endpoint can be pointed at, by id: seeded over HTTP by the integration tests. A "ghost" world has
 * the same shape with random ids that exist nowhere, which is what an id of another tenant must be indistinguishable from.
 */
record TenantWorld(AbstractApiIntegrationTest.Tenant tenant, AbstractApiIntegrationTest.Person member, AbstractApiIntegrationTest.Person spare,
                   String entryLevelId, String secondLevelId, String thirdLevelId, String venueId, String groupId, String planId,
                   String subscriptionId, String paymentId, String sessionId, String bookingId, String approvableRequestId,
                   String rejectableRequestId) {

    String adminToken() {
        return tenant.admin().token();
    }

    String adminId() {
        return tenant.admin().memberId();
    }

    /** The same shape, with ids that exist nowhere. */
    static TenantWorld ghost() {
        return new TenantWorld(tenant("ghost"), person(), person(), id(), id(), id(), id(), id(), id(), id(), id(), id(), id(), id(), id());
    }

    private static AbstractApiIntegrationTest.Tenant tenant(String name) {
        return new AbstractApiIntegrationTest.Tenant(name, id(), person());
    }

    private static AbstractApiIntegrationTest.Person person() {
        return new AbstractApiIntegrationTest.Person("nobody@example.com", "no-token", id());
    }

    private static String id() {
        return UUID.randomUUID().toString();
    }
}
