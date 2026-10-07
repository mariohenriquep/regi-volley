package com.regivolley.api.domain.shared;

/**
 * Marker for an entity that lives inside an aggregate (for example {@code Booking} inside
 * {@code Session}): it has an identity but is created and changed only through its aggregate
 * root and has no repository of its own.
 */
public interface Entity {
}
