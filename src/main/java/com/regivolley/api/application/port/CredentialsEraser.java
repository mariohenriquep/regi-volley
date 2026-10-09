package com.regivolley.api.application.port;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

/**
 * Outbound port for the credentials side of an erasure (RGPD, threat model section 6): when a member is erased, the login
 * account built on their email address must go too. The future erasure use case calls it after anonymising the member and
 * going through the last-administrator guard (architecture.md section 13); there is no such use case yet.
 *
 * <p>Implementations delete the member's membership and its refresh tokens and links; when it was the user's only
 * membership they delete the account itself (email and password hash). Erasing a member who has no credentials is a no-op.
 */
public interface CredentialsEraser {

    void erase(AssociationId associationId, MemberId memberId);
}
