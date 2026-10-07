package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link EmailAddress} and {@link PhoneNumber}: basic shape checks and no personal data in toString. */
class ContactDataTest {

    @Nested
    class Email {

        @ParameterizedTest
        @ValueSource(strings = {"ana@example.com", "ana.silva+volley@mail.example.pt", "a@b.co"})
        void acceptsABasicallyWellFormedAddress(String raw) {
            // Arrange
            // (raw value from the source)

            // Act
            EmailAddress email = EmailAddress.of(raw);

            // Assert
            assertThat(email.value()).isEqualTo(raw);
        }

        @Test
        void trimsAndLowercases() {
            // Arrange
            String raw = "  Ana.Silva@Example.COM ";

            // Act
            EmailAddress email = EmailAddress.of(raw);

            // Assert
            assertThat(email.value()).isEqualTo("ana.silva@example.com");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "ana", "ana@", "@example.com", "ana@example", "ana@@example.com",
                "ana @example.com", "ana@example..com", "ana@.com", "ana@example."})
        void rejectsAMalformedAddress(String raw) {
            // Arrange
            Executable act = () -> EmailAddress.of(raw);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("email");
        }

        @Test
        void rejectsAnAddressLongerThan254Characters() {
            // Arrange
            String tooLong = "a".repeat(250) + "@b.co";
            Executable act = () -> EmailAddress.of(tooLong);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("email");
        }

        @Test
        void acceptsTheMaximumLength() {
            // Arrange
            String atMax = "a".repeat(EmailAddress.MAX_LENGTH - "@b.co".length()) + "@b.co";

            // Act
            EmailAddress email = EmailAddress.of(atMax);

            // Assert
            assertThat(email.value()).hasSize(EmailAddress.MAX_LENGTH);
        }

        @Test
        void toStringDoesNotRevealTheAddress() {
            // Arrange
            EmailAddress email = EmailAddress.of("ana@example.com");

            // Act
            String text = email.toString();

            // Assert
            assertThat(text).doesNotContain("ana").doesNotContain("example");
        }

        @Test
        void theErasedPlaceholderIsValidAndUniquePerMember() {
            // Arrange
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();

            // Act
            EmailAddress firstEmail = EmailAddress.anonymisedFor(first);
            EmailAddress secondEmail = EmailAddress.anonymisedFor(second);

            // Assert
            assertThat(firstEmail.value()).endsWith("@anonymised.invalid").contains(first.toString());
            assertThat(firstEmail).isNotEqualTo(secondEmail);
        }
    }

    @Nested
    class Phone {

        @ParameterizedTest
        @ValueSource(strings = {"912345678", "+351912345678", "+44 7911 123456", "912-345-678", "+1234567890123"})
        void acceptsAnOptionalPlusAndNineToFifteenDigits(String raw) {
            // Arrange
            String expected = raw.replaceAll("[\\s-]", "");

            // Act
            PhoneNumber phone = PhoneNumber.of(raw);

            // Assert
            assertThat(phone.value()).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "12345678", "1234567890123456", "91234567a", "++351912345678", "351+912345678", "(351)912345678"})
        void rejectsAMalformedNumber(String raw) {
            // Arrange
            Executable act = () -> PhoneNumber.of(raw);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("phone");
        }

        @Test
        void acceptsTheMaximumOfFifteenDigits() {
            // Arrange
            String atMax = "+" + "1".repeat(15);

            // Act
            PhoneNumber phone = PhoneNumber.of(atMax);

            // Assert
            assertThat(phone.value()).isEqualTo(atMax);
        }

        @ParameterizedTest
        @ValueSource(strings = {"000000000", "+000000000000", "0000 0000 0"})
        void rejectsAnAllZeroNumber(String raw) {
            // Arrange
            Executable act = () -> PhoneNumber.of(raw);

            // Act
            InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

            // Assert
            assertThat(ex.field()).isEqualTo("phone");
        }

        @Test
        void toStringDoesNotRevealTheNumber() {
            // Arrange
            PhoneNumber phone = PhoneNumber.of("912345678");

            // Act
            String text = phone.toString();

            // Assert
            assertThat(text).doesNotContain("912345678");
        }
    }
}
