package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;

/**
 * The last-administrator rule: an association must always keep an active administrator, or nobody could ever
 * manage it again.
 *
 * <p>Two attempts that each remove an administrator (two revocations, a revocation and a deactivation, ...) must not
 * run side by side, or each could count the other's administrator as the one that stays. Row locks on the
 * administrators cannot give that (under READ COMMITTED the role rows are judged against an old snapshot, and an entity
 * already loaded would not be refreshed). So every attempt that may remove an administrator starts with
 * {@link #serialise}: it takes the <em>association row</em> lock before loading any member, which queues such attempts
 * one after the other. {@link #requireAnotherActiveAdmin} then counts with a fresh scalar query, which sees whatever
 * the attempt ahead committed.
 */
final class AdminGuard {

    private final AssociationRepository associations;
    private final MemberRepository members;

    AdminGuard(AssociationRepository associations, MemberRepository members) {
        this.associations = associations;
        this.members = members;
    }

    /** First step of an attempt that may remove an administrator: wait for any other such attempt to finish. */
    void serialise(AssociationId associationId) {
        associations.findByIdForUpdate(associationId).orElseThrow(() -> new AssociationNotFoundException(associationId));
    }

    /**
     * @param leaving an active administrator who is about to stop being one (deactivated, or losing the role)
     * @throws LastAdministratorException if nobody else is an active administrator
     */
    void requireAnotherActiveAdmin(AssociationId associationId, MemberId leaving) {
        boolean someoneElse = members.findActiveAdminIds(associationId).stream().anyMatch(id -> !id.equals(leaving));
        if (!someoneElse) {
            throw new LastAdministratorException();
        }
    }
}
