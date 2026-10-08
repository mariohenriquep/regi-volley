package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.JoinRequestNotPossibleException;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;

import java.util.List;
import java.util.Optional;

/** Port for {@link JoinRequest} aggregates. Every read is scoped to one association (architecture.md section 8). */
public interface JoinRequestRepository {

    Optional<JoinRequest> findById(AssociationId associationId, JoinRequestId id);

    /** The request of this association still awaiting a decision from that email, if any (US-05 duplicate check). */
    Optional<JoinRequest> findPendingByEmail(AssociationId associationId, EmailAddress email);

    /** The requests still awaiting a decision, oldest first (US-06). */
    List<JoinRequest> findPending(AssociationId associationId);

    /**
     * Inserts a new request or updates an existing one, and returns it as stored, with its new version (each
     * save moves it forward by one); keep working with the returned instance.
     *
     * @throws JoinRequestNotPossibleException if another request of the association from the same email is
     *         already pending (a race the use case's {@link #findPendingByEmail} check cannot rule out)
     * @throws JoinRequestModifiedConcurrentlyException if the stored request is no longer at the version of
     *         {@code request} (an approval and a rejection at once cannot both be stored), if it has a version
     *         but no stored row in its association, or if its row could not be locked in time; nothing is
     *         written. Retry in a new transaction.
     */
    JoinRequest save(JoinRequest request);
}
