package com.regivolley.api.application.port;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

/**
 * Outbound port that gives a new member a way to sign in (threat model D-11, section 6): a login account for the member's
 * email address, a membership that stays PENDING, and a single-use activation link sent to that address. The application
 * layer knows nothing about passwords, tokens or the security framework; it only says who needs an account.
 *
 * <p>Called only after the member was committed, and a failure never undoes that: use cases log it by ids and carry on
 * (risk R3: the operator, or the administrator's "resend activation", repeats it). Implementations are idempotent: asking
 * twice for the same member issues a fresh link and creates nothing twice.
 */
public interface AccountProvisioner {

    void provision(AssociationId associationId, MemberId memberId, EmailAddress email);
}
