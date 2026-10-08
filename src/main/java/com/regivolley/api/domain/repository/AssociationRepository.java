package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.AssociationModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ShortName;

import java.util.List;
import java.util.Optional;

/**
 * Port for {@link Association} aggregates, levels included. An association is the tenant itself,
 * so it is read by its own id; the short name lookups are the public ones (US-24, architecture.md
 * section 8), the only reads that do not start from an authenticated tenant.
 */
public interface AssociationRepository {

    Optional<Association> findById(AssociationId id);

    Optional<Association> findByShortName(ShortName shortName);

    boolean existsByShortName(ShortName shortName);

    /**
     * The ids of every association, for system-wide jobs that then work tenant by tenant (the daily
     * session generation, US-10). Not a business read: it exposes no tenant data, only who the tenants are.
     */
    List<AssociationId> findAllIds();

    /**
     * Inserts a new association or updates an existing one with its levels, and returns it as stored,
     * with its new version (each save moves it forward by one); keep working with the returned instance.
     *
     * @throws ShortNameAlreadyTakenException if another association uses the short name (a race the
     *         use case's {@link #existsByShortName} check cannot rule out)
     * @throws AssociationModifiedConcurrentlyException if the stored association is no longer at the
     *         version of {@code association}, if it has a version but no stored row, or if its row could
     *         not be locked in time; nothing is written. Retry in a new transaction.
     */
    Association save(Association association);
}
