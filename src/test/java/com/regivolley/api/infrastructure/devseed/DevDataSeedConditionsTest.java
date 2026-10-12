package com.regivolley.api.infrastructure.devseed;

import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.usecase.ApproveJoinRequestUseCase;
import com.regivolley.api.application.usecase.AssignPlanUseCase;
import com.regivolley.api.application.usecase.CreatePlanUseCase;
import com.regivolley.api.application.usecase.CreateTrainingGroupUseCase;
import com.regivolley.api.application.usecase.CreateVenueUseCase;
import com.regivolley.api.application.usecase.GenerateSessionsUseCase;
import com.regivolley.api.application.usecase.GrantRoleUseCase;
import com.regivolley.api.application.usecase.RecordPaymentUseCase;
import com.regivolley.api.application.usecase.RegisterAssociationUseCase;
import com.regivolley.api.application.usecase.SubmitJoinRequestUseCase;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Issue #59: the dev data initializer exists only under profile {@code dev} without {@code prod}, and only while
 * {@code regi-volley.dev-seed.enabled} is true.
 */
class DevDataSeedConditionsTest {

    private static final List<Class<?>> COLLABORATORS = List.of(
            AssociationRepository.class, RegisterAssociationUseCase.class, SubmitJoinRequestUseCase.class,
            ApproveJoinRequestUseCase.class, GrantRoleUseCase.class, CreateVenueUseCase.class,
            CreateTrainingGroupUseCase.class, CreatePlanUseCase.class, AssignPlanUseCase.class, RecordPaymentUseCase.class,
            GenerateSessionsUseCase.class, UserAccountStore.class, MembershipStore.class, EmailLinkStore.class,
            PasswordHasher.class, SecretGenerator.class, TransactionRunner.class, Clock.class);

    private ApplicationContextRunner runner() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(DevDataInitializer.class, DevDataSeeder.class, DevAccountActivator.class);
        for (Class<?> type : COLLABORATORS) {
            runner = withMock(runner, type);
        }
        return runner;
    }

    private static <T> ApplicationContextRunner withMock(ApplicationContextRunner runner, Class<T> type) {
        return runner.withBean(type, () -> mock(type));
    }

    @Test
    void existsUnderDevWhenEnabled() {
        // Arrange
        ApplicationContextRunner dev = runner().withPropertyValues("spring.profiles.active=dev", "regi-volley.dev-seed.enabled=true");

        // Act
        dev.run(context -> {
            // Assert
            assertThat(context).hasNotFailed().hasSingleBean(DevDataInitializer.class);
        });
    }

    @Test
    void isAbsentWithNoProfile() {
        // Arrange
        ApplicationContextRunner none = runner().withPropertyValues("regi-volley.dev-seed.enabled=true");

        // Act
        none.run(context -> {
            // Assert
            assertThat(context).doesNotHaveBean(DevDataInitializer.class).doesNotHaveBean(DevDataSeeder.class);
        });
    }

    @Test
    void isAbsentUnderTheTestProfile() {
        // Arrange
        ApplicationContextRunner test = runner().withPropertyValues("spring.profiles.active=test", "regi-volley.dev-seed.enabled=true");

        // Act
        test.run(context -> {
            // Assert
            assertThat(context).doesNotHaveBean(DevDataInitializer.class).doesNotHaveBean(DevDataSeeder.class);
        });
    }

    @Test
    void isAbsentWhenProdIsActiveToo() {
        // Arrange
        ApplicationContextRunner prodAndDev = runner().withPropertyValues("spring.profiles.active=prod,dev", "regi-volley.dev-seed.enabled=true");

        // Act
        prodAndDev.run(context -> {
            // Assert
            assertThat(context).doesNotHaveBean(DevDataInitializer.class).doesNotHaveBean(DevDataSeeder.class)
                    .doesNotHaveBean(DevAccountActivator.class);
        });
    }

    @Test
    void isAbsentWhenDisabled() {
        // Arrange
        ApplicationContextRunner disabled = runner().withPropertyValues("spring.profiles.active=dev", "regi-volley.dev-seed.enabled=false");

        // Act
        disabled.run(context -> {
            // Assert
            assertThat(context).doesNotHaveBean(DevDataInitializer.class).doesNotHaveBean(DevAccountActivator.class);
        });
    }

    @Test
    void isAbsentWhenTheSwitchIsNotConfigured() {
        // Arrange
        ApplicationContextRunner unset = runner().withPropertyValues("spring.profiles.active=dev");

        // Act
        unset.run(context -> {
            // Assert
            assertThat(context).doesNotHaveBean(DevDataInitializer.class);
        });
    }
}
