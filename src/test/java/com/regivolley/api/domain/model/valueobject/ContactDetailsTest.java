package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactDetailsTest {

    private static final EmailAddress EMAIL = EmailAddress.of("ana@example.com");
    private static final PhoneNumber PHONE = PhoneNumber.of("912345678");

    @Test
    void holdsTheTrimmedNameEmailAndPhone() {
        // Arrange
        // (padded name)

        // Act
        ContactDetails contact = ContactDetails.of("  Ana Silva ", EMAIL, PHONE);

        // Assert
        assertThat(contact.name()).isEqualTo("Ana Silva");
        assertThat(contact.email()).isEqualTo(EMAIL);
        assertThat(contact.phone()).contains(PHONE);
    }

    @Test
    void acceptsANameOfTheMaximumLength() {
        // Arrange
        String atMax = "x".repeat(ContactDetails.MAX_NAME_LENGTH);

        // Act
        ContactDetails contact = ContactDetails.of(atMax, EMAIL, PHONE);

        // Assert
        assertThat(contact.name()).hasSize(ContactDetails.MAX_NAME_LENGTH);
    }

    @Test
    void rejectsANameThatIsTooLong() {
        // Arrange
        String tooLong = "x".repeat(ContactDetails.MAX_NAME_LENGTH + 1);
        Executable act = () -> ContactDetails.of(tooLong, EMAIL, PHONE);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void requiresAName(String name) {
        // Arrange
        Executable act = () -> ContactDetails.of(name, EMAIL, PHONE);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("name");
    }

    @Test
    void requiresEmailAndPhoneWhenCollected() {
        // Arrange
        Executable noEmail = () -> ContactDetails.of("Ana", null, PHONE);
        Executable noPhone = () -> ContactDetails.of("Ana", EMAIL, null);

        // Act
        NullPointerException email = assertThrows(NullPointerException.class, noEmail);
        NullPointerException phone = assertThrows(NullPointerException.class, noPhone);

        // Assert
        assertThat(email.getMessage()).contains("email");
        assertThat(phone.getMessage()).contains("phone");
    }

    @Test
    void theErasedPlaceholderHasNoPhoneAndAnEmailUniquePerId() {
        // Arrange
        UUID id = UUID.randomUUID();

        // Act
        ContactDetails erased = ContactDetails.anonymisedFor(id);
        ContactDetails other = ContactDetails.anonymisedFor(UUID.randomUUID());

        // Assert
        assertThat(erased.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
        assertThat(erased.email().value()).isEqualTo("anonymised-" + id + "@anonymised.invalid");
        assertThat(erased.phone()).isEmpty();
        assertThat(erased).isNotEqualTo(other);
    }

    @Test
    void canBeRebuiltWithoutAPhoneForAnErasedRecord() {
        // Arrange
        // (persisted erased record)

        // Act
        ContactDetails contact = ContactDetails.reconstruct("Anonymised member", EMAIL, null);

        // Assert
        assertThat(contact.phone()).isEmpty();
    }

    @Test
    void comparesByValueAndNeverPrintsPersonalData() {
        // Arrange
        ContactDetails first = ContactDetails.of("Ana Silva", EMAIL, PHONE);
        ContactDetails same = ContactDetails.of("Ana Silva", EMAIL, PHONE);
        ContactDetails other = ContactDetails.of("Rui", EMAIL, PHONE);

        // Act
        String text = first.toString();

        // Assert
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other).isNotEqualTo("not contact details");
        assertThat(text).doesNotContain("Ana").doesNotContain("Silva").doesNotContain("ana@example.com")
                .doesNotContain("912345678");
    }
}
