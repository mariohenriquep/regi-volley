package com.regivolley.api.application.result;

/** Outcome of the all-associations run: how many tenants were processed, how many failed and sessions created in total. */
public record GenerationRunReport(int associationsProcessed, int associationsFailed, int sessionsCreated) {
}
