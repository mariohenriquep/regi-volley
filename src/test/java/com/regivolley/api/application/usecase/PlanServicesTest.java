package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.domain.exception.InvalidPlanException;
import com.regivolley.api.domain.exception.LevelNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.PlanNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-19: create and edit plans (there is deliberately no delete: subscriptions keep pointing at their plan). */
@ExtendWith(MockitoExtension.class)
class PlanServicesTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private PlanRepository plans;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member admin;
    private Member coach;
    private Plan plan;
    private CreatePlanUseCase create;
    private EditPlanUseCase edit;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        plan = Plan.create(association.id(), "Monthly", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null);
        create = new CreatePlanService(members, associations, plans, transactions);
        edit = new EditPlanService(members, associations, plans, transactions);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        lenient().when(plans.findById(association.id(), plan.id())).thenReturn(Optional.of(plan));
        lenient().when(plans.save(any(Plan.class))).thenAnswer(returnsFirstArg());
    }

    @Test
    void createsAPackPlanInTheAdministratorsAssociation() {
        // Arrange
        LevelId beginner = Data.level(association, "Beginner");
        CreatePlanCommand command = new CreatePlanCommand(Data.actor(admin), "Pack of 10", PlanTerms.pack(10, Set.of(beginner)),
                Money.ofCents(4500), 90);

        // Act
        Plan created = create.execute(command);

        // Assert
        assertThat(created.associationId()).isEqualTo(association.id());
        assertThat(created.type()).isEqualTo(PlanType.PACK);
        assertThat(created.terms().credits()).isEqualTo(10);
        assertThat(created.allowedLevels()).containsExactly(beginner);
        assertThat(created.price()).isEqualTo(Money.ofCents(4500));
        assertThat(created.validityDays()).hasValue(90);
        verify(plans).save(created);
    }

    @Test
    void aPlanCannotAllowALevelOfAnotherAssociation() {
        // Arrange
        Executable act = () -> create.execute(new CreatePlanCommand(Data.actor(admin), "Pack",
                PlanTerms.pack(10, Set.of(LevelId.generate())), Money.ofCents(4500), 90));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(plans, never()).save(any(Plan.class));
    }

    @Test
    void anInvalidPlanIsRejectedByTheDomain() {
        // Arrange
        Executable act = () -> create.execute(new CreatePlanCommand(Data.actor(admin), "Pack",
                PlanTerms.pack(10, Set.of()), Money.ofCents(4500), null));

        // Act
        assertThrows(InvalidPlanException.class, act);

        // Assert
        verify(plans, never()).save(any(Plan.class));
    }

    @Test
    void onlyAnAdministratorMayCreateOrEdit() {
        // Arrange
        Executable createAttempt = () -> create.execute(new CreatePlanCommand(Data.actor(coach), "P",
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(1), null));
        Executable editAttempt = () -> edit.execute(new EditPlanCommand(Data.actor(coach), plan.id(), "P",
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(1), null));

        // Act
        assertThrows(NotAllowedException.class, createAttempt);
        assertThrows(NotAllowedException.class, editAttempt);

        // Assert
        verify(plans, never()).save(any(Plan.class));
    }

    @Test
    void editsEverythingAnAdministratorMayChangeAndKeepsTheIdentity() {
        // Arrange
        EditPlanCommand command = new EditPlanCommand(Data.actor(admin), plan.id(), "Twice a week",
                PlanTerms.monthlyNPerWeek(2, Set.of()), Money.ofCents(2500), null);

        // Act
        Plan edited = edit.execute(command);

        // Assert
        assertThat(edited.id()).isEqualTo(plan.id());
        assertThat(edited.name()).isEqualTo("Twice a week");
        assertThat(edited.type()).isEqualTo(PlanType.MONTHLY_N_PER_WEEK);
        assertThat(edited.price()).isEqualTo(Money.ofCents(2500));
        verify(plans).save(edited);
    }

    @Test
    void editingAPlanOfAnotherAssociationIsNotFound() {
        // Arrange
        Executable act = () -> edit.execute(new EditPlanCommand(Data.actor(admin), PlanId.generate(), "P",
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(1), null));

        // Act
        assertThrows(PlanNotFoundException.class, act);

        // Assert
        verify(plans, never()).save(any(Plan.class));
    }

    @Test
    void editingCannotAllowALevelOfAnotherAssociation() {
        // Arrange
        Executable act = () -> edit.execute(new EditPlanCommand(Data.actor(admin), plan.id(), "P",
                PlanTerms.monthlyUnlimited(Set.of(LevelId.generate())), Money.ofCents(1), null));

        // Act
        assertThrows(LevelNotFoundException.class, act);

        // Assert
        verify(plans, never()).save(any(Plan.class));
    }

    @Test
    void editRetriesInANewTransactionOnAConflict() {
        // Arrange
        when(plans.save(any(Plan.class)))
                .thenThrow(new PlanModifiedConcurrentlyException(plan.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        Plan edited = edit.execute(new EditPlanCommand(Data.actor(admin), plan.id(), "New name",
                PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(3000), null));

        // Assert
        assertThat(edited.name()).isEqualTo("New name");
        assertThat(transactions.opened()).isEqualTo(2);
    }
}
