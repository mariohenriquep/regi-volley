package com.regivolley.api.infrastructure.devseed;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.AssociationRegistered;
import com.regivolley.api.application.result.JoinRequestApproval;
import com.regivolley.api.application.result.JoinRequestSubmitted;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.application.result.SessionGenerationReport;
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
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates the development association (issue #59) by going through the use cases, so every rule and invariant applies exactly as
 * it does over HTTP:
 * <ol>
 *   <li>{@code RegisterAssociation}: Volei Lisboa with three levels, its founder the administrator;</li>
 *   <li>the coach joins like any applicant ({@code SubmitJoinRequest}) and the administrator approves ({@code ApproveJoinRequest}),
 *       then is granted COACH ({@code GrantRole});</li>
 *   <li>both accounts are activated with the dev password without an email ({@link DevAccountActivator});</li>
 *   <li>a venue, a Monday / Wednesday 20:00 (Lisbon) group coached by the <em>coach</em> - the administrator is deliberately not
 *       its coach, since a session's coach cannot book it (RN-02) - and a 10-credit pack;</li>
 *   <li>the pack is assigned to the administrator and paid in full, and the sessions are generated.</li>
 * </ol>
 * The use cases take an {@link Actor} from the administrator's {@link AuthenticatedActor}, the one place an actor is built. Not
 * transactional as a whole (each use case has its own transaction): a seed that stops half way leaves the association in place, and
 * the dev database is reset to start again. Logs ids only.
 */
@DevSeedOnly
class DevDataSeeder {

    static final String SHORT_NAME = "volei-lisboa";
    static final String ADMIN_EMAIL = "admin@volei-lisboa.test";
    static final String COACH_EMAIL = "coach@volei-lisboa.test";

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String POLICY_VERSION = "2026-01";

    private final AssociationRepository associations;
    private final RegisterAssociationUseCase registerAssociation;
    private final SubmitJoinRequestUseCase submitJoinRequest;
    private final ApproveJoinRequestUseCase approveJoinRequest;
    private final GrantRoleUseCase grantRole;
    private final CreateVenueUseCase createVenue;
    private final CreateTrainingGroupUseCase createTrainingGroup;
    private final CreatePlanUseCase createPlan;
    private final AssignPlanUseCase assignPlan;
    private final RecordPaymentUseCase recordPayment;
    private final GenerateSessionsUseCase generateSessions;
    private final DevAccountActivator accounts;
    private final Clock clock;
    private final String password;

    DevDataSeeder(AssociationRepository associations, RegisterAssociationUseCase registerAssociation,
                  SubmitJoinRequestUseCase submitJoinRequest, ApproveJoinRequestUseCase approveJoinRequest,
                  GrantRoleUseCase grantRole, CreateVenueUseCase createVenue, CreateTrainingGroupUseCase createTrainingGroup,
                  CreatePlanUseCase createPlan, AssignPlanUseCase assignPlan, RecordPaymentUseCase recordPayment,
                  GenerateSessionsUseCase generateSessions, DevAccountActivator accounts, Clock clock,
                  @Value("${regi-volley.dev-seed.password:dev-password-volley}") String password) {
        this.associations = associations;
        this.registerAssociation = registerAssociation;
        this.submitJoinRequest = submitJoinRequest;
        this.approveJoinRequest = approveJoinRequest;
        this.grantRole = grantRole;
        this.createVenue = createVenue;
        this.createTrainingGroup = createTrainingGroup;
        this.createPlan = createPlan;
        this.assignPlan = assignPlan;
        this.recordPayment = recordPayment;
        this.generateSessions = generateSessions;
        this.accounts = accounts;
        this.clock = clock;
        this.password = password;
    }

    void seed() {
        AssociationRegistered registered = registerAssociation.execute(new RegisterAssociationCommand("Volei Lisboa", SHORT_NAME, null,
                "Lisbon", "info@volei-lisboa.test", java.util.List.of("Beginner", "Intermediate", "Advanced"), "Dev Admin", ADMIN_EMAIL,
                "912345678", true, POLICY_VERSION));
        AssociationId associationId = registered.associationId();
        MemberId adminId = registered.founderId();
        UUID adminUser = accounts.activate(associationId, adminId, ADMIN_EMAIL, password);
        Actor admin = new AuthenticatedActor(adminUser, associationId, adminId).actor();

        MemberId coachId = addCoach(admin);
        TrainingGroup group = createGroup(admin, associationId, coachId);
        Subscription subscription = giveAdminAPaidPack(admin, adminId);
        SessionGenerationReport sessions = generateSessions.execute(new GenerateSessionsCommand(associationId));

        LOG.info("Dev seed created: association={} admin={} coach={} group={} subscription={} sessions={}", associationId, adminId,
                coachId, group.id(), subscription.id(), sessions.sessionsCreated());
    }

    /** The coach asks to join, is approved at the entry level, activated, and given COACH. */
    private MemberId addCoach(Actor admin) {
        JoinRequestSubmitted submitted = submitJoinRequest.execute(new SubmitJoinRequestCommand(SHORT_NAME, "Dev Coach", COACH_EMAIL,
                "912345679", true, POLICY_VERSION));
        JoinRequestApproval approval = approveJoinRequest.execute(new ApproveJoinRequestCommand(admin, submitted.requestId()));
        MemberId coachId = approval.member().id();
        accounts.activate(admin.associationId(), coachId, COACH_EMAIL, password);
        grantRole.execute(new GrantRoleCommand(admin, coachId, MemberRole.COACH));
        return coachId;
    }

    private TrainingGroup createGroup(Actor admin, AssociationId associationId, MemberId coachId) {
        Venue venue = createVenue.execute(new CreateVenueCommand(admin, "Pavilhao Dev", "Rua da Bola 1, Lisbon", 2));
        Association association = associations.findById(associationId)
                .orElseThrow(() -> new IllegalStateException("The association was just registered: " + associationId));
        Set<LevelId> allLevels = association.levels().stream().map(Level::id).collect(Collectors.toSet());
        WeeklySchedule schedule = WeeklySchedule.of(slot(DayOfWeek.MONDAY), slot(DayOfWeek.WEDNESDAY));
        return createTrainingGroup.execute(new CreateTrainingGroupCommand(admin, "Evening training", allLevels, venue.id(), schedule,
                12, coachId));
    }

    private Subscription giveAdminAPaidPack(Actor admin, MemberId adminId) {
        Plan plan = createPlan.execute(new CreatePlanCommand(admin, "10-session pack", PlanTerms.pack(10, Set.of()),
                Money.ofEuros(new BigDecimal("30.00")), 90));
        Subscription subscription = assignPlan.execute(new AssignPlanCommand(admin, adminId, plan.id(), null));
        LocalDate today = LocalDate.ofInstant(clock.instant(), ScheduleZone.LISBON.zoneId());
        PaymentRecorded paid = recordPayment.execute(new RecordPaymentCommand(admin, subscription.id(), subscription.price(), today,
                PaymentMethod.CASH));
        return paid.subscription();
    }

    private static WeeklySlot slot(DayOfWeek day) {
        return new WeeklySlot(day, LocalTime.of(20, 0), Duration.ofMinutes(90));
    }
}
