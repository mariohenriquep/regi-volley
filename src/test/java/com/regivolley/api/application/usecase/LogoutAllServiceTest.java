package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutAllCommand;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.result.SessionTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-4, U3: logout-all ends every login and changes the stamp, so access tokens die before their expiry. */
class LogoutAllServiceTest {

    private final SessionTestSupport t = new SessionTestSupport();
    private final LogoutAllService logoutAll = new LogoutAllService(t.tokens, t.h.stores.users, t.h.secrets, t.h.transactions, t.h.clock);

    @Test
    void revokesEveryFamilyAndRotatesTheSecurityStamp() {
        // Arrange
        SessionTokens laptop = t.open();
        SessionTokens phone = t.open();
        String oldStamp = t.h.stores.users.byId.get(t.user.id()).securityStamp();

        // Act
        logoutAll.execute(new LogoutAllCommand(t.user.id()));

        // Assert
        Executable laptopRefresh = () -> t.refresh(laptop.refreshToken());
        Executable phoneRefresh = () -> t.refresh(phone.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, laptopRefresh);
        assertThrows(InvalidRefreshTokenException.class, phoneRefresh);
        String newStamp = t.h.stores.users.byId.get(t.user.id()).securityStamp();
        assertThat(newStamp).isNotEqualTo(oldStamp).hasSize(43);
        assertThat(t.h.stores.users.byId.get(t.user.id()).updatedAt()).isEqualTo(t.h.clock.instant());
    }

    @Test
    void anAccessTokenIssuedBeforeIsRejectedByTheVerifierAfterwards() {
        // Arrange
        String oldStamp = t.user.securityStamp();
        logoutAll.execute(new LogoutAllCommand(t.user.id()));
        String newStamp = t.h.stores.users.byId.get(t.user.id()).securityStamp();

        // Act
        var withOldStamp = t.h.verifier.rejectionReason(t.user.id(), t.h.association.id(), t.h.member.id(), oldStamp);
        var withNewStamp = t.h.verifier.rejectionReason(t.user.id(), t.h.association.id(), t.h.member.id(), newStamp);

        // Assert
        assertThat(withOldStamp).contains("STAMP_MISMATCH");
        assertThat(withNewStamp).isEmpty();
    }
}
