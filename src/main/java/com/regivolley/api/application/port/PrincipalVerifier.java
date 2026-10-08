package com.regivolley.api.application.port;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for "may this user still act as this member?" (threat model D-6): the account exists and is enabled, the
 * membership is confirmed, and the member is active and not anonymised. The same check runs on every request; a login and a refresh
 * ask it too, so a deactivated member can neither sign in nor refresh.
 */
public interface PrincipalVerifier {

    /**
     * @param securityStamp the stamp the caller holds (a refresh passes the account's own, since it checks standing, not a token)
     * @return empty when allowed; otherwise a short reason code for the audit log (never shown to the client)
     */
    Optional<String> rejectionReason(UUID userId, AssociationId associationId, MemberId memberId, String securityStamp);
}
