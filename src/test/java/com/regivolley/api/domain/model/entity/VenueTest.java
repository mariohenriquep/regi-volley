package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidCourtCountException;
import com.regivolley.api.domain.factory.VenueFactory;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VenueTest {
    private static final AssociationId ASSOCIATION = AssociationId.generate();

    @Test
    void editKeepsIdentityTenantAndVersion() {
        // Arrange
        Venue venue = VenueFactory.reconstitute(VenueId.generate(), ASSOCIATION, "Old", "Old street", 1, 6L);

        // Act
        Venue edited = venue.edit("New", "New street", 3);

        // Assert
        assertThat(edited.id()).isEqualTo(venue.id());
        assertThat(edited.associationId()).isEqualTo(ASSOCIATION);
        assertThat(edited.version()).isEqualTo(6L);
        assertThat(edited.name()).isEqualTo("New");
        assertThat(edited.address()).isEqualTo("New street");
        assertThat(edited.courts()).isEqualTo(3);
        assertThat(venue.name()).isEqualTo("Old");
    }

    @Test
    void editRevalidates() {
        // Arrange
        Venue venue = VenueFactory.create(ASSOCIATION, "Pavilhao", "Rua A", 1);
        Executable act = () -> venue.edit("Pavilhao", "Rua A", 0);

        // Act
        InvalidCourtCountException ex = assertThrows(InvalidCourtCountException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("court");
    }

    @Test
    void equalityIsByIdAndToStringPrintsIdsOnly() {
        // Arrange
        Venue venue = VenueFactory.create(ASSOCIATION, "Pavilhao", "Rua A", 1);
        Venue renamed = venue.edit("Other", "Rua B", 2);
        Venue another = VenueFactory.create(ASSOCIATION, "Pavilhao", "Rua A", 1);

        // Act
        String text = venue.toString();

        // Assert
        assertThat(venue).isEqualTo(renamed).hasSameHashCodeAs(renamed).isNotEqualTo(another).isNotEqualTo("x");
        assertThat(text).contains(venue.id().toString()).doesNotContain("Rua A");
    }
}
