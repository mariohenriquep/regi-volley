package com.regivolley.api.application.result;

/** What a purge run did: associations handled, associations that failed (logged by id) and requests anonymised. */
public record JoinRequestPurgeReport(int associationsProcessed, int associationsFailed, int requestsAnonymised) {
}
