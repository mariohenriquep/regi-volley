package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.port.AccessTokenIssuer;
import com.regivolley.api.application.usecase.ActivateAccountUseCase;
import com.regivolley.api.application.usecase.AddLevelUseCase;
import com.regivolley.api.application.usecase.ApproveJoinRequestUseCase;
import com.regivolley.api.application.usecase.ArchiveTrainingGroupUseCase;
import com.regivolley.api.application.usecase.AssignPlanUseCase;
import com.regivolley.api.application.usecase.BookSessionUseCase;
import com.regivolley.api.application.usecase.CancelBookingUseCase;
import com.regivolley.api.application.usecase.CancelSessionUseCase;
import com.regivolley.api.application.usecase.ChangeEntryLevelUseCase;
import com.regivolley.api.application.usecase.ChangeMemberLevelUseCase;
import com.regivolley.api.application.usecase.ChangeSessionCapacityUseCase;
import com.regivolley.api.application.usecase.CreatePlanUseCase;
import com.regivolley.api.application.usecase.CreateTrainingGroupUseCase;
import com.regivolley.api.application.usecase.CreateVenueUseCase;
import com.regivolley.api.application.usecase.DeactivateMemberUseCase;
import com.regivolley.api.application.usecase.DeleteVenueUseCase;
import com.regivolley.api.application.usecase.EditPlanUseCase;
import com.regivolley.api.application.usecase.EditTrainingGroupUseCase;
import com.regivolley.api.application.usecase.EditVenueUseCase;
import com.regivolley.api.application.usecase.GetSessionRosterUseCase;
import com.regivolley.api.application.usecase.GrantRoleUseCase;
import com.regivolley.api.application.usecase.ListBookableSessionsUseCase;
import com.regivolley.api.application.usecase.ListSubscriptionsByPaymentStatusUseCase;
import com.regivolley.api.application.usecase.MarkAttendanceUseCase;
import com.regivolley.api.application.usecase.MarkSubscriptionOverdueUseCase;
import com.regivolley.api.application.usecase.MemberHistoryUseCase;
import com.regivolley.api.application.usecase.MyPlanUseCase;
import com.regivolley.api.application.usecase.RecordPaymentUseCase;
import com.regivolley.api.application.usecase.RegisterAssociationUseCase;
import com.regivolley.api.application.usecase.RejectJoinRequestUseCase;
import com.regivolley.api.application.usecase.RenameLevelUseCase;
import com.regivolley.api.application.usecase.ResendActivationLinkUseCase;
import com.regivolley.api.application.usecase.ReorderLevelsUseCase;
import com.regivolley.api.application.usecase.ReversePaymentUseCase;
import com.regivolley.api.application.usecase.RevokeRoleUseCase;
import com.regivolley.api.application.usecase.SubmitJoinRequestUseCase;
import com.regivolley.api.application.usecase.GetPublicAssociationUseCase;
import com.regivolley.api.application.usecase.ListPendingJoinRequestsUseCase;
import com.regivolley.api.application.usecase.LoginUseCase;
import com.regivolley.api.application.usecase.LogoutAllUseCase;
import com.regivolley.api.application.usecase.LogoutUseCase;
import com.regivolley.api.application.usecase.RefreshSessionUseCase;
import com.regivolley.api.application.usecase.RequestPasswordResetUseCase;
import com.regivolley.api.application.usecase.ResetPasswordUseCase;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * The real security filter chain, the real advice and the real controllers over a mocked {@link MemberRepository} and the
 * in-memory account lookup: no database. Subclasses share one Spring context unless they add configuration.
 */
@WebMvcTest
@Import({SecurityConfiguration.class, JwtKeyConfiguration.class, SecurityTestConfiguration.class})
@ActiveProfiles("test")
public abstract class AbstractSecuredWebTest {

    protected static final String STAMP = "stamp-1";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected AccessTokenIssuer issuer;
    @Autowired
    protected InMemorySecurityAccountLookup accounts;
    @Autowired
    protected JwtKeySet keys;
    @MockitoBean
    protected MemberRepository members;
    // The credential endpoints' use cases: the slice tests the HTTP contract, the services have their own tests.
    @MockitoBean
    protected LoginUseCase loginUseCase;
    @MockitoBean
    protected RefreshSessionUseCase refreshSessionUseCase;
    @MockitoBean
    protected LogoutUseCase logoutUseCase;
    @MockitoBean
    protected LogoutAllUseCase logoutAllUseCase;
    @MockitoBean
    protected ActivateAccountUseCase activateAccountUseCase;
    @MockitoBean
    protected ResetPasswordUseCase resetPasswordUseCase;
    @MockitoBean
    protected RequestPasswordResetUseCase requestPasswordResetUseCase;

    // The booking and administration use cases of the 26c controllers, mocked the same way.
    @MockitoBean
    protected AddLevelUseCase addLevelUseCase;
    @MockitoBean
    protected ApproveJoinRequestUseCase approveJoinRequestUseCase;
    @MockitoBean
    protected ArchiveTrainingGroupUseCase archiveTrainingGroupUseCase;
    @MockitoBean
    protected AssignPlanUseCase assignPlanUseCase;
    @MockitoBean
    protected BookSessionUseCase bookSessionUseCase;
    @MockitoBean
    protected CancelBookingUseCase cancelBookingUseCase;
    @MockitoBean
    protected CancelSessionUseCase cancelSessionUseCase;
    @MockitoBean
    protected ChangeEntryLevelUseCase changeEntryLevelUseCase;
    @MockitoBean
    protected ChangeMemberLevelUseCase changeMemberLevelUseCase;
    @MockitoBean
    protected ChangeSessionCapacityUseCase changeSessionCapacityUseCase;
    @MockitoBean
    protected CreatePlanUseCase createPlanUseCase;
    @MockitoBean
    protected CreateTrainingGroupUseCase createTrainingGroupUseCase;
    @MockitoBean
    protected CreateVenueUseCase createVenueUseCase;
    @MockitoBean
    protected DeactivateMemberUseCase deactivateMemberUseCase;
    @MockitoBean
    protected DeleteVenueUseCase deleteVenueUseCase;
    @MockitoBean
    protected EditPlanUseCase editPlanUseCase;
    @MockitoBean
    protected EditTrainingGroupUseCase editTrainingGroupUseCase;
    @MockitoBean
    protected EditVenueUseCase editVenueUseCase;
    @MockitoBean
    protected GetSessionRosterUseCase getSessionRosterUseCase;
    @MockitoBean
    protected GrantRoleUseCase grantRoleUseCase;
    @MockitoBean
    protected ListBookableSessionsUseCase listBookableSessionsUseCase;
    @MockitoBean
    protected ListSubscriptionsByPaymentStatusUseCase listSubscriptionsByPaymentStatusUseCase;
    @MockitoBean
    protected MarkAttendanceUseCase markAttendanceUseCase;
    @MockitoBean
    protected MarkSubscriptionOverdueUseCase markSubscriptionOverdueUseCase;
    @MockitoBean
    protected MemberHistoryUseCase memberHistoryUseCase;
    @MockitoBean
    protected MyPlanUseCase myPlanUseCase;
    @MockitoBean
    protected RecordPaymentUseCase recordPaymentUseCase;
    @MockitoBean
    protected RegisterAssociationUseCase registerAssociationUseCase;
    @MockitoBean
    protected RejectJoinRequestUseCase rejectJoinRequestUseCase;
    @MockitoBean
    protected RenameLevelUseCase renameLevelUseCase;
    @MockitoBean
    protected ReorderLevelsUseCase reorderLevelsUseCase;
    @MockitoBean
    protected ResendActivationLinkUseCase resendActivationLinkUseCase;
    @MockitoBean
    protected ReversePaymentUseCase reversePaymentUseCase;
    @MockitoBean
    protected RevokeRoleUseCase revokeRoleUseCase;
    @MockitoBean
    protected SubmitJoinRequestUseCase submitJoinRequestUseCase;
    @MockitoBean
    protected GetPublicAssociationUseCase getPublicAssociationUseCase;
    @MockitoBean
    protected ListPendingJoinRequestsUseCase listPendingJoinRequestsUseCase;

    protected Association association;
    protected Member member;
    protected UUID userId;

    @BeforeEach
    void seedAnActiveMember() {
        accounts.clear();
        association = SecurityFixtures.association();
        member = SecurityFixtures.active(association);
        userId = UUID.randomUUID();
        accounts.registerActive(userId, association.id(), member.id(), STAMP);
        when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
    }

    /** A valid token for the seeded member. */
    protected String validToken() {
        return issuer.issue(userId, association.id(), member.id(), STAMP).value();
    }

    protected String bearer() {
        return "Bearer " + validToken();
    }
}
