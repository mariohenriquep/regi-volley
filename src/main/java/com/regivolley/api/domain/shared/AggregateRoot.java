package com.regivolley.api.domain.shared;

/**
 * Marker for an aggregate root: the entity that owns a consistency boundary, enforces its
 * invariants and is the only thing a repository loads and saves (docs/architecture.md section 4).
 * Other aggregates refer to it by id, never by object.
 */
public interface AggregateRoot {
}
