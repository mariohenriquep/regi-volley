package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.CommonPasswordList;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-8: 10 to 128 characters (NFKC), no composition rules, not on the common list, not the email or the club. */
class PasswordPolicyTest {

    private final CommonPasswordList common = password -> Set.of("password123", "qwertyuiop").contains(password.toLowerCase(Locale.ROOT));
    private final PasswordPolicy policy = new PasswordPolicy(common);
    private final EmailAddress email = EmailAddress.of("anamariasilva@example.com");

    private Executable checking(String password) {
        return () -> policy.check(password, email, List.of("Volley Club"));
    }

    @Test
    void acceptsAPasswordOfTenCharactersWithNoCompositionRules() {
        // Arrange
        String password = "aaaaaaaaab";

        // Act
        policy.check(password, email, List.of());

        // Assert
        assertThat(policy.normalise(password)).hasSize(10);
    }

    @Test
    void acceptsAPasswordOf128Characters() {
        // Arrange
        String password = "x7".repeat(64);

        // Act
        policy.check(password, email, List.of());

        // Assert
        assertThat(policy.normalise(password)).hasSize(128);
    }

    @Test
    void refusesNineCharactersAndNamesTheField() {
        // Arrange
        Executable act = checking("abcdefg12");

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
        assertThat(ex.getMessage()).contains("10").contains("128");
    }

    @Test
    void refusesMoreThan128Characters() {
        // Arrange
        Executable act = checking("x7".repeat(64) + "x");

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"          "})
    void refusesNullAndBlank(String password) {
        // Arrange
        Executable act = checking(password);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "PASSWORD123", "Qwertyuiop"})
    void refusesCommonPasswordsWhateverTheirCase(String password) {
        // Arrange
        Executable act = checking(password);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.getMessage()).containsIgnoringCase("common");
    }

    @ParameterizedTest
    @ValueSource(strings = {"anamariasilva@example.com", "anamariasilva", "ANAMARIASILVA", "VOLLEY club"})
    void refusesTheEmailAddressItsLocalPartAndTheAssociationName(String password) {
        // Arrange
        Executable act = checking(password);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
        assertThat(ex.getMessage()).contains("email");
    }

    @Test
    void measuresLengthAfterUnicodeNormalisationSoTenFullwidthDigitsAreTen() {
        // Arrange - ten fullwidth digits normalise (NFKC) to ten ASCII digits
        String ten = "\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18\uFF19\uFF10";

        // Act
        policy.check(ten, email, List.of());

        // Assert
        assertThat(policy.normalise(ten)).isEqualTo("1234567890");
    }

    @Test
    void nineFullwidthDigitsAreStillNine() {
        // Arrange
        Executable act = checking("\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18\uFF19");

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
    }

    @Test
    void normalisesToNfkc() {
        // Arrange
        String fullwidth = "\uFF21\uFF22\uFF23";

        // Act
        String normalised = policy.normalise(fullwidth);

        // Assert
        assertThat(normalised).isEqualTo("ABC");
    }
}
