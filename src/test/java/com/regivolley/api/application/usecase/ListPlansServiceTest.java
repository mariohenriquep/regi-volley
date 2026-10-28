package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPlansQuery;
import com.regivolley.api.application.result.PlanListEntry;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.factory.PlanFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-19: the administrator's plan list with the levels each plan gives access to. */
@ExtendWith(MockitoExtension.class)
class ListPlansServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private PlanRepository plans;

    private Association association;
    private Member admin;
    private ListPlansUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new ListPlansService(members, associations, plans);
    }

    @Test
    void listsThePlansInRepositoryOrderWithTheirAllowedLevelsLowestFirst() {
        // Arrange
        LevelId beginner = Data.level(association, "Beginner");
        LevelId advanced = Data.level(association, "Advanced");
        Plan restricted = PlanFactory.create(association.id(), "Pack", PlanTerms.pack(10, Set.of(advanced, beginner)), Money.ofCents(4500), 90);
        Plan open = PlanFactory.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        when(plans.findAllByAssociation(association.id())).thenReturn(List.of(restricted, open));

        // Act
        List<PlanListEntry> entries = useCase.execute(new ListPlansQuery(Data.actor(admin)));

        // Assert
        assertThat(entries).extracting(PlanListEntry::plan).containsExactly(restricted, open);
        assertThat(entries.get(0).allowedLevels()).extracting(Level::name).containsExactly("Beginner", "Advanced");
        assertThat(entries.get(1).allowedLevels()).isEmpty();
    }

    @Test
    void anAllowedLevelThatCannotBeFoundIsLeftOutInsteadOfFailingTheList() {
        // Arrange
        LevelId beginner = Data.level(association, "Beginner");
        Plan plan = PlanFactory.create(association.id(), "Pack", PlanTerms.pack(10, Set.of(beginner, LevelId.generate())), Money.ofCents(4500), 90);
        when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        when(plans.findAllByAssociation(association.id())).thenReturn(List.of(plan));

        // Act
        List<PlanListEntry> entries = useCase.execute(new ListPlansQuery(Data.actor(admin)));

        // Assert
        assertThat(entries.get(0).allowedLevels()).extracting(Level::name).containsExactly("Beginner");
    }

    @Test
    void aMemberIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member plain = Data.member(association);
        when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(new ListPlansQuery(Data.actor(plain)));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(plans, associations);
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListPlansQuery(Data.actor(stranger)));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verifyNoInteractions(plans, associations);
    }
}
