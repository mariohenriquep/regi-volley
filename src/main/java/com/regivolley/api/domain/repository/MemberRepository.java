package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port for {@link Member} aggregates, roles and level history included. Every read is scoped to one association (architecture.md section 8). */
public interface MemberRepository {

    Optional<Member> findById(AssociationId associationId, MemberId id);

    /** The members of this association among these ids (unknown or foreign ids are simply absent), in no particular order. */
    List<Member> findByIds(AssociationId associationId, Collection<MemberId> ids);

    /**
     * As {@link #findById}, but takes the member's row lock for the rest of the current transaction, so
     * that two transactions working on the same member queue up instead of racing (RN-07: one member
     * booking two overlapping sessions at once). Meant to be the first thing a transaction does; the
     * lock is released at its commit or rollback. Must run inside a transaction.
     */
    Optional<Member> findByIdForUpdate(AssociationId associationId, MemberId id);

    /**
     * The ids of the association's active administrators, ordered by id, read with a fresh query (ids, not
     * entities, so nothing already loaded in the transaction can answer for the database). The last-administrator
     * guard counts through it after taking {@link AssociationRepository#findByIdForUpdate}, which is what
     * serialises it against other removals of an administrator.
     */
    List<MemberId> findActiveAdminIds(AssociationId associationId);

    /** The member of this association with that email; the same email in another association is another person. */
    Optional<Member> findByEmail(AssociationId associationId, EmailAddress email);

    /**
     * Inserts a new member or updates an existing one, and returns it as stored, with its new version (each
     * save moves it forward by one); keep working with the returned instance.
     *
     * @throws MemberEmailAlreadyUsedException if another member of the association has the email (a race
     *         the use case's {@link #findByEmail} check cannot rule out)
     * @throws MemberModifiedConcurrentlyException if the stored member is no longer at the version of
     *         {@code member} (e.g. it was erased in between: a stale copy never overwrites an erasure), if it
     *         has a version but no stored row in its association, or if its row could not be locked in time;
     *         nothing is written. Retry in a new transaction.
     */
    Member save(Member member);
}
