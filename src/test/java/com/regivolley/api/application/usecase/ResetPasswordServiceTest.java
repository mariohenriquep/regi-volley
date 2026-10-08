package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.InvalidLinkException;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.result.SessionTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;

import static com.regivolley.api.application.usecase.LinkTestSupport.NEW_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-11a: a reset sets a new password, ends every session and kills old access tokens, and never confirms a membership. */
class ResetPasswordServiceTest {

    private final LinkTestSupport t = new LinkTestSupport();

    private Executable resetting(String token) {
        return () -> t.reset(token, NEW_PASSWORD);
    }

    @Test
    void resettingChangesThePasswordRevokesEverySessionAndRotatesTheStamp() {
        // Arrange
        AccountLink link = t.resetLinkForActiveUser();
        SessionTestSupport sessions = new SessionTestSupport(t.h);
        SessionTokens laptop = sessions.open(t.user);
        SessionTokens phone = sessions.open(t.user);
        String oldStamp = t.user.securityStamp();

        // Act
        t.reset(link.token(), NEW_PASSWORD);

        // Assert
        assertThat(t.h.stores.users.byId.get(t.user.id()).passwordHash()).isEqualTo(t.h.hasher.hash(NEW_PASSWORD));
        assertThat(t.h.stores.users.byId.get(t.user.id()).securityStamp()).isNotEqualTo(oldStamp);
        assertThat(sessions.stored(laptop.refreshToken()).isRevoked()).isTrue();
        assertThat(sessions.stored(phone.refreshToken()).isRevoked()).isTrue();
        Executable replay = () -> sessions.refresh(laptop.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, replay);
    }

    @Test
    void anAccessTokenCarryingTheOldStampIsRejectedAfterwards() {
        // Arrange
        AccountLink link = t.resetLinkForActiveUser();
        String oldStamp = t.user.securityStamp();
        t.reset(link.token(), NEW_PASSWORD);

        // Act
        var rejection = t.h.verifier.rejectionReason(t.user.id(), t.h.association.id(), t.h.member.id(), oldStamp);

        // Assert
        assertThat(rejection).contains("STAMP_MISMATCH");
    }

    @Test
    void aResetLinkExpiresAfterThirtyMinutes() {
        // Arrange
        AccountLink link = t.resetLinkForActiveUser();
        t.h.clock.advance(Duration.ofMinutes(30).plusSeconds(1));
        Executable act = resetting(link.token());

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void aResetLinkWorksOnlyOnce() {
        // Arrange
        AccountLink link = t.resetLinkForActiveUser();
        t.reset(link.token(), NEW_PASSWORD);
        Executable again = resetting(link.token());

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, again);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void anActivationTokenCannotReset() {
        // Arrange
        t.resetLinkForActiveUser();
        AccountLink activation = t.issuer.issue(t.user, t.h.membershipOf(t.user), AccountLinkPurpose.ACTIVATION);
        Executable act = resetting(activation.token());

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void resettingDoesNotConfirmAPendingMembership() {
        // Arrange - the mailbox is proved, but only an activation link confirms a membership (L4)
        t.user = t.h.pendingUser();
        AccountLink reset = t.issuer.issue(t.user, null, AccountLinkPurpose.PASSWORD_RESET);

        // Act
        t.reset(reset.token(), NEW_PASSWORD);

        // Assert
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.PENDING);
    }
}
