package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutCommand;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.result.SessionTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogoutServiceTest {

    private final SessionTestSupport t = new SessionTestSupport();
    private final LogoutService logout = new LogoutService(t.tokens, t.h.secrets, t.h.transactions, t.h.clock);

    @Test
    void logoutRevokesTheFamilyOfThePresentedTokenOnly() {
        // Arrange
        SessionTokens laptop = t.open();
        SessionTokens phone = t.open();

        // Act
        logout.execute(new LogoutCommand(laptop.refreshToken()));

        // Assert
        assertThat(t.stored(laptop.refreshToken()).isRevoked()).isTrue();
        assertThat(t.stored(phone.refreshToken()).isRevoked()).isFalse();
        Executable reuse = () -> t.refresh(laptop.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, reuse);
    }

    @Test
    void logoutOfAnUnknownTokenIsAQuietNoOp() {
        // Arrange
        SessionTokens live = t.open();
        LogoutCommand unknown = new LogoutCommand(t.h.secrets.newSecret());

        // Act
        logout.execute(unknown);

        // Assert
        assertThat(t.stored(live.refreshToken()).isRevoked()).isFalse();
    }

    @Test
    void logoutWithoutATokenIsAQuietNoOpThatOpensNoTransaction() {
        // Arrange
        LogoutCommand none = new LogoutCommand(null);

        // Act
        logout.execute(none);

        // Assert
        assertThat(t.h.transactions.opened()).isZero();
    }
}
