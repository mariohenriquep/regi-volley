package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetPublicAssociationQuery;
import com.regivolley.api.application.result.PublicAssociationPage;
import com.regivolley.api.domain.exception.ShortNameNotFoundException;
import com.regivolley.api.domain.factory.TrainingGroupFactory;
import com.regivolley.api.domain.factory.VenueFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-24: the public page shows only what the association chose to publish, resolved by short name. */
@ExtendWith(MockitoExtension.class)
class GetPublicAssociationServiceTest {

    @Mock
    private AssociationRepository associations;
    @Mock
    private TrainingGroupRepository groups;
    @Mock
    private VenueRepository venues;

    private Association association;
    private GetPublicAssociationUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        useCase = new GetPublicAssociationService(associations, groups, venues);
    }

    @Test
    void showsTheLevelsInRankOrderTheActiveGroupsWithTheirScheduleAndVenueAndTheContact() {
        // Arrange
        Member coach = Data.coach(association);
        Venue venue = VenueFactory.create(association.id(), "Pavilion One", "Rua A 1, Lisbon", 2);
        TrainingGroup active = TrainingGroupFactory.create(association.id(), "Wednesday Beginners",
                Set.of(Data.level(association, "Beginner")), venue.id(),
                WeeklySchedule.of(new WeeklySlot(DayOfWeek.WEDNESDAY, LocalTime.of(20, 0), Duration.ofMinutes(90))), 12, coach.id());
        TrainingGroup archived = active.archive();
        when(associations.findByShortName(association.shortName())).thenReturn(Optional.of(association));
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(active, archived));
        when(venues.findAllByAssociation(association.id())).thenReturn(List.of(venue));

        // Act
        PublicAssociationPage page = useCase.execute(new GetPublicAssociationQuery(association.shortName().value()));

        // Assert
        assertThat(page.name()).isEqualTo(association.name());
        assertThat(page.shortName()).isEqualTo(association.shortName().value());
        assertThat(page.contactEmail()).isEqualTo("info@club.example");
        assertThat(page.levels()).extracting(level -> level.name()).containsExactly("Beginner", "Intermediate", "Advanced");
        assertThat(page.venues()).singleElement().satisfies(v -> {
            assertThat(v.name()).isEqualTo("Pavilion One");
            assertThat(v.courts()).isEqualTo(2);
        });
        assertThat(page.groups()).singleElement().satisfies(g -> {
            assertThat(g.name()).isEqualTo("Wednesday Beginners");
            assertThat(g.venueName()).isEqualTo("Pavilion One");
            assertThat(g.levelNames()).containsExactly("Beginner");
            assertThat(g.schedule()).singleElement().satisfies(slot -> {
                assertThat(slot.dayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
                assertThat(slot.startTime()).isEqualTo(LocalTime.of(20, 0));
                assertThat(slot.durationMinutes()).isEqualTo(90);
            });
        });
    }

    @Test
    void readsTheGroupsAndVenuesOfTheAssociationFoundByShortNameOnly() {
        // Arrange
        when(associations.findByShortName(association.shortName())).thenReturn(Optional.of(association));
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of());
        when(venues.findAllByAssociation(association.id())).thenReturn(List.of());

        // Act
        PublicAssociationPage page = useCase.execute(new GetPublicAssociationQuery("  " + association.shortName().value().toUpperCase() + " "));

        // Assert
        assertThat(page.groups()).isEmpty();
        assertThat(page.venues()).isEmpty();
    }

    @Test
    void aGroupWhoseVenueIsGoneIsStillListedWithoutAVenueName() {
        // Arrange
        Member coach = Data.coach(association);
        TrainingGroup group = Data.group(association, coach, "Beginner");
        when(associations.findByShortName(association.shortName())).thenReturn(Optional.of(association));
        when(groups.findAllByAssociation(association.id())).thenReturn(List.of(group));
        when(venues.findAllByAssociation(association.id())).thenReturn(List.of());

        // Act
        PublicAssociationPage page = useCase.execute(new GetPublicAssociationQuery(association.shortName().value()));

        // Assert
        assertThat(page.groups()).singleElement().satisfies(g -> assertThat(g.venueName()).isNull());
    }

    @Test
    void anUnknownShortNameIsNotFoundAndNothingElseIsRead() {
        // Arrange
        when(associations.findByShortName(ShortName.of("no-such-club"))).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new GetPublicAssociationQuery("no-such-club"));

        // Act
        ShortNameNotFoundException ex = assertThrows(ShortNameNotFoundException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo("no-such-club");
        verifyNoInteractions(groups, venues);
    }

    @Test
    void aTextThatCannotBeAShortNameIsNotFoundAndNothingIsRead() {
        // Arrange
        Executable act = () -> useCase.execute(new GetPublicAssociationQuery("../etc/passwd"));

        // Act
        ShortNameNotFoundException ex = assertThrows(ShortNameNotFoundException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo("../etc/passwd");
        verifyNoInteractions(associations, groups, venues);
    }
}
