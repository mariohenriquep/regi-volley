package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.domain.model.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gives a freshly committed member their login account, and never lets that fail the use case: the member exists whatever
 * happens here (threat model P7, risk R3). A failure is logged by ids and exception type only - the message could quote an
 * address - and the account is created later by repeating the call.
 */
final class AccountProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(AccountProvisioning.class);

    private AccountProvisioning() {
    }

    static void afterCommit(AccountProvisioner provisioner, Member member) {
        try {
            provisioner.provision(member.associationId(), member.id(), member.email());
        } catch (RuntimeException e) {
            LOG.warn("Provisioning credentials failed after the commit: association={} member={} cause={}",
                    member.associationId(), member.id(), e.getClass().getSimpleName());
        }
    }
}
