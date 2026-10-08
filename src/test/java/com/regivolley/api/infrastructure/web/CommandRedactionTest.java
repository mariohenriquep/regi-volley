package com.regivolley.api.infrastructure.web;

import com.regivolley.api.application.command.ActivateAccountCommand;
import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.command.LogoutCommand;
import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.command.RequestPasswordResetCommand;
import com.regivolley.api.application.command.ResetPasswordCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Commands carry credentials and addresses: whatever logs one prints nothing. */
class CommandRedactionTest {

    @Test
    void noCredentialCommandPrintsItsContents() {
        // Arrange
        String secret = "s3cret-value-xyz";

        // Act
        String text = String.join("|", new LoginCommand("ana@example.com", secret, "203.0.113.5").toString(),
                new RefreshSessionCommand(secret).toString(), new LogoutCommand(secret).toString(),
                new ActivateAccountCommand(secret, secret).toString(), new ResetPasswordCommand(secret, secret).toString(),
                new RequestPasswordResetCommand("ana@example.com").toString());

        // Assert
        assertThat(text).doesNotContain(secret).doesNotContain("ana@example.com").doesNotContain("203.0.113.5");
    }
}
