package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.CredentialsEraser;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The credentials side of an erasure (threat model section 6): deletes the member's membership, and the account itself - email,
 * password hash, refresh tokens, links - when that was the user's only membership. The member is looked up with the association,
 * so another tenant's member id reaches nothing. No use case calls it yet; it is here so the erasure issue only has to use it.
 */
@Service
public class CredentialsEraserService implements CredentialsEraser {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialsEraserService.class);

    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final TransactionRunner transactions;

    public CredentialsEraserService(UserAccountStore users, MembershipStore memberships, TransactionRunner transactions) {
        this.users = users;
        this.memberships = memberships;
        this.transactions = transactions;
    }

    @Override
    public void erase(AssociationId associationId, MemberId memberId) {
        transactions.inNewTransaction(() -> {
            memberships.findByMember(associationId, memberId).ifPresent(membership -> {
                if (memberships.findByUser(membership.userId()).size() > 1) {
                    memberships.deleteById(membership.id());
                    LOG.info("Credentials erased: membership of association={} member={}, the account has others", associationId, memberId);
                } else {
                    users.deleteById(membership.userId());
                    LOG.info("Credentials erased: account of association={} member={}", associationId, memberId);
                }
            });
            return null;
        });
    }
}
