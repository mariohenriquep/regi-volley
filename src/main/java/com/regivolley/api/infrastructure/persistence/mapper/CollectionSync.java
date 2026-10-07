package com.regivolley.api.infrastructure.persistence.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Brings a collection of a managed JPA entity in line with the desired content while touching it
 * as little as possible, so Hibernate only issues SQL for the rows that really changed (and an
 * unchanged collection does not even look dirty).
 */
final class CollectionSync {

    private CollectionSync() {
    }

    /** Keeps the rows that are still wanted, adds the new ones, drops the rest. */
    static <T> void replace(Set<T> target, Collection<T> desired) {
        target.retainAll(desired);
        target.addAll(desired);
    }

    /** Replaces the content only if it differs; order matters. */
    static <T> void replace(List<T> target, List<T> desired) {
        if (!target.equals(desired)) {
            target.clear();
            target.addAll(desired);
        }
    }
}
