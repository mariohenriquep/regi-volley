package com.regivolley.api.application.port;

import com.regivolley.api.application.identity.AccessToken;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.UUID;

/**
 * Outbound port that mints the short-lived access token of a session (threat model D-4). The adapter signs and decides the
 * format and lifetime; the application hands over only ids and the account's current security stamp, never roles or personal data.
 */
public interface AccessTokenIssuer {

    AccessToken issue(UUID userId, AssociationId associationId, MemberId memberId, String securityStamp);
}
