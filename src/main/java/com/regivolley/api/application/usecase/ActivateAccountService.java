package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ActivateAccountCommand;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.port.CommonPasswordList;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Consumes a ACTIVATION link (threat model D-11): the link must be valid and unspent, the password must satisfy the policy, and
 * consuming it sets the password, changes the security stamp and revokes every refresh token. The rules are in
 * {@link CredentialLinkConsumer}. Every unusable link is the same {@link com.regivolley.api.application.exception.InvalidLinkException}.
 */
@Service
public class ActivateAccountService implements ActivateAccountUseCase {

    private final CredentialLinkConsumer consumer;

    public ActivateAccountService(EmailLinkStore links, UserAccountStore users, MembershipStore memberships, AssociationRepository associations,
                           CommonPasswordList commonPasswords, PasswordHasher hasher, RefreshTokenStore refreshTokens,
                           SecretGenerator secrets, TransactionRunner transactions, Clock clock) {
        this.consumer = new CredentialLinkConsumer(links, users, memberships, associations, commonPasswords, hasher, refreshTokens,
                secrets, transactions, clock);
    }

    @Override
    public Void execute(ActivateAccountCommand command) {
        consumer.consume(command.token(), command.password(), AccountLinkPurpose.ACTIVATION);
        return null;
    }
}
