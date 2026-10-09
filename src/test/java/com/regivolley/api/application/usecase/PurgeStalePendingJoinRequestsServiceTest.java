package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.result.JoinRequestPurgeReport;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** RGPD data minimisation: a join request nobody decided within 30 days is erased (anonymised), whatever association it is in. */
@ExtendWith(MockitoExtension.class)
class PurgeStalePendingJoinRequestsServiceTest {

    private static final Instant CUTOFF = Data.NOW.minus(Duration.ofDays(30));

    @Mock
    private AssociationRepository associations;
    @Mock
    private JoinRequestRepository joinRequests;

    private final DirectTransactions transactions = new DirectTransactions();
    private PurgeStalePendingJoinRequestsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PurgeStalePendingJoinRequestsService(associations, joinRequests, transactions, Data.CLOCK);
        lenient().when(joinRequests.save(any(JoinRequest.class))).thenAnswer(returnsFirstArg());
    }

    private static JoinRequest stale(AssociationId associationId, String name) {
        return JoinRequestFactory.create(associationId, ContactDetails.of(name, EmailAddress.of(name.toLowerCase() + "@example.com"),
                PhoneNumber.of("912345678")), GdprConsent.record(true, "2026-01", Data.CLOCK), Data.CLOCK);
    }

    @Test
    void anonymisesTheStaleRequestsOfEveryAssociationAndCountsThem() {
        // Arrange
        AssociationId first = AssociationId.generate();
        AssociationId second = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(first, second));
        when(joinRequests.findPendingRequestedBefore(eq(first), eq(CUTOFF), any(Integer.class))).thenReturn(List.of(stale(first, "Rita"), stale(first, "Rui")));
        when(joinRequests.findPendingRequestedBefore(eq(second), eq(CUTOFF), any(Integer.class))).thenReturn(List.of(stale(second, "Ana")));

        // Act
        JoinRequestPurgeReport report = useCase.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        ArgumentCaptor<JoinRequest> saved = ArgumentCaptor.forClass(JoinRequest.class);
        verify(joinRequests, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(request -> {
            assertThat(request.isAnonymised()).isTrue();
            assertThat(request.status()).isEqualTo(JoinRequestStatus.REJECTED);
            assertThat(request.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
            assertThat(request.consent().policyVersion()).isEqualTo("2026-01");
        });
        assertThat(report).isEqualTo(new JoinRequestPurgeReport(2, 0, 3));
    }

    @Test
    void keepsGoingInBatchesWhileFullBatchesComeBack() {
        // Arrange
        AssociationId id = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(id));
        List<JoinRequest> full = new ArrayList<>();
        for (int i = 0; i < PurgeStalePendingJoinRequestsService.BATCH; i++) {
            full.add(stale(id, "Person" + i));
        }
        when(joinRequests.findPendingRequestedBefore(id, CUTOFF, PurgeStalePendingJoinRequestsService.BATCH))
                .thenReturn(full).thenReturn(List.of(stale(id, "Last")));

        // Act
        JoinRequestPurgeReport report = useCase.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        assertThat(report.requestsAnonymised()).isEqualTo(PurgeStalePendingJoinRequestsService.BATCH + 1);
        verify(joinRequests, times(2)).findPendingRequestedBefore(id, CUTOFF, PurgeStalePendingJoinRequestsService.BATCH);
    }

    @Test
    void aRequestDecidedAtTheSameMomentIsSkippedAndTheRestAreStillErased() {
        // Arrange
        AssociationId id = AssociationId.generate();
        JoinRequest raced = stale(id, "Raced");
        JoinRequest other = stale(id, "Other");
        when(associations.findAllIds()).thenReturn(List.of(id));
        when(joinRequests.findPendingRequestedBefore(eq(id), eq(CUTOFF), any(Integer.class))).thenReturn(List.of(raced, other));
        when(joinRequests.save(any(JoinRequest.class))).thenThrow(new JoinRequestModifiedConcurrentlyException(raced.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        JoinRequestPurgeReport report = useCase.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        assertThat(report).isEqualTo(new JoinRequestPurgeReport(1, 0, 1));
    }

    @Test
    void oneFailingAssociationNeverStopsTheOthers() {
        // Arrange
        AssociationId broken = AssociationId.generate();
        AssociationId healthy = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(broken, healthy));
        when(joinRequests.findPendingRequestedBefore(eq(broken), eq(CUTOFF), any(Integer.class))).thenThrow(new IllegalStateException("db"));
        when(joinRequests.findPendingRequestedBefore(eq(healthy), eq(CUTOFF), any(Integer.class))).thenReturn(List.of(stale(healthy, "Ana")));

        // Act
        JoinRequestPurgeReport report = useCase.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        assertThat(report).isEqualTo(new JoinRequestPurgeReport(1, 1, 1));
    }

    @Test
    void nothingStaleMeansNothingSaved() {
        // Arrange
        AssociationId id = AssociationId.generate();
        when(associations.findAllIds()).thenReturn(List.of(id));
        when(joinRequests.findPendingRequestedBefore(eq(id), eq(CUTOFF), any(Integer.class))).thenReturn(List.of());

        // Act
        JoinRequestPurgeReport report = useCase.execute(new PurgeStalePendingJoinRequestsCommand());

        // Assert
        assertThat(report).isEqualTo(new JoinRequestPurgeReport(1, 0, 0));
        verify(joinRequests, never()).save(any(JoinRequest.class));
    }
}
