package com.regivolley.api.infrastructure.notification;

import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.JoinRequestFactory;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.factory.SessionFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.testsupport.LogCapture;
import com.regivolley.testsupport.SmtpTestServer;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Issue #40: every notice of the {@code Notifier} port reaches the right mailbox over a real SMTP conversation, with nothing hostile in it. */
@ExtendWith(MockitoExtension.class)
class SmtpNotifierTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    /** 17:30 UTC on 14 October 2026 is 18:30 in Lisbon (summer time). */
    private static final Instant SESSION_START = Instant.parse("2026-10-14T17:30:00Z");
    private static final String HOSTILE_NAME = "<img src=x onerror=alert(1)>Club & \"Sons\"";

    @Mock
    private MemberRepository members;
    @Mock
    private SessionRepository sessions;
    @Mock
    private AssociationRepository associations;
    @Mock
    private JoinRequestRepository joinRequests;

    private SmtpTestServer smtp;
    private MailDispatcher dispatcher;
    private SmtpNotifier notifier;
    private Association association;

    @BeforeEach
    void start() {
        smtp = new SmtpTestServer().start();
        dispatcher = new MailDispatcher(1, 50, List.of(Duration.ofMillis(10), Duration.ofMillis(10)));
        notifier = notifierOver(smtp.sender());
        association = AssociationFactory.create("Volley Club", "volley-club", null, "Lisbon", "info@club.example", List.of("Beginner", "Advanced"));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
    }

    @AfterEach
    void stop() {
        dispatcher.shutdown();
        smtp.stop();
    }

    private SmtpNotifier notifierOver(JavaMailSenderImpl sender) {
        return new SmtpNotifier(dispatcher, new MailDelivery(sender, "no-reply@example.org"), MailTemplates.load(),
                members, sessions, associations, joinRequests);
    }

    private Member member(String name, String email, MemberRole... roles) {
        Set<MemberRole> held = roles.length == 0 ? Set.of(MemberRole.MEMBER) : Set.of(roles);
        Member member = MemberFactory.create(association, ContactDetails.of(name, EmailAddress.of(email), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), held, CLOCK);
        lenient().when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        return member;
    }

    private Session session() {
        Session session = SessionFactory.create(association.id(), TrainingGroupId.generate(), member("Coach", "coach@example.com").id(),
                SESSION_START, SESSION_START.plus(Duration.ofMinutes(90)), 12);
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        return session;
    }

    private void awaitIdle() throws InterruptedException {
        assertThat(dispatcher.awaitIdle(Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void aPromotedMemberIsToldAtTheirContactAddressWhichSessionAndWhere() {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Session session = session();

        // Act
        notifier.bookingPromoted(association.id(), session.id(), ana.id());

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly("ana.silva@example.com");
        assertThat(mail.subject()).isEqualTo("You were moved up from the waitlist");
        assertThat(mail.text()).contains("Volley Club").contains("14/10/2026 18:30").doesNotContain("Ana Silva");
        assertThat(mail.html()).contains("<strong>Volley Club</strong>").contains("14/10/2026 18:30");
    }

    @Test
    void everyMemberOfACancelledSessionIsToldWithTheCoachsReason() {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Session session = session();

        // Act
        notifier.sessionCancelled(association.id(), session.id(), ana.id(), "The hall is closed for\r\nmaintenance");

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly("ana.silva@example.com");
        assertThat(mail.subject()).isEqualTo("A session you signed up for was cancelled");
        assertThat(mail.text()).contains("Volley Club", "14/10/2026 18:30", "Reason given: The hall is closed for maintenance");
    }

    @Test
    void theNoShowWarningGoesToTheMemberAndToEveryActiveAdministratorOnce() {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Member boss = member("Boss One", "boss@example.com", MemberRole.ADMIN, MemberRole.MEMBER);
        Member deputy = member("Deputy Two", "deputy@example.com", MemberRole.ADMIN);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(boss.id(), deputy.id()));
        when(members.findByIds(any(), any())).thenAnswer(call -> {
            Collection<?> ids = call.getArgument(1);
            return List.of(boss, deputy).stream().filter(m -> ids.contains(m.id())).toList();
        });

        // Act
        notifier.noShowLimitReached(association.id(), ana.id(), 3);

        // Assert
        List<SmtpTestServer.Received> mails = smtp.awaitMessages(3);
        Map<String, SmtpTestServer.Received> byRecipient = new HashMap<>();
        mails.forEach(mail -> byRecipient.put(mail.to().get(0), mail));
        assertThat(byRecipient).containsOnlyKeys("ana.silva@example.com", "boss@example.com", "deputy@example.com");
        assertThat(byRecipient.get("ana.silva@example.com").subject()).isEqualTo("You reached the monthly no-show limit");
        assertThat(byRecipient.get("ana.silva@example.com").text()).contains("Volley Club", "3 this month").doesNotContain("Boss One");
        assertThat(byRecipient.get("boss@example.com").subject()).isEqualTo("A member reached the monthly no-show limit");
        assertThat(byRecipient.get("boss@example.com").text()).contains("Ana Silva", "No-shows this month: 3", "Volley Club");
        assertThat(byRecipient.get("deputy@example.com").html()).contains("<strong>Ana Silva</strong>");
    }

    @Test
    void anAdministratorWhoIsTheOffendingMemberGetsTheirOwnWarningOnly() throws Exception {
        // Arrange
        Member boss = member("Boss One", "boss@example.com", MemberRole.ADMIN, MemberRole.MEMBER);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(boss.id()));
        when(members.findByIds(any(), any())).thenReturn(List.of(boss));

        // Act
        notifier.noShowLimitReached(association.id(), boss.id(), 3);

        // Assert
        List<SmtpTestServer.Received> mails = smtp.awaitMessages(1);
        awaitIdle();
        assertThat(smtp.received()).hasSize(1);
        assertThat(mails.get(0).subject()).isEqualTo("You reached the monthly no-show limit");
    }

    @Test
    void anApprovedMemberIsWelcomedAtTheirAddress() {
        // Arrange
        Member rita = member("Rita Costa", "rita@example.com");

        // Act
        notifier.memberApproved(association.id(), rita.id());

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly("rita@example.com");
        assertThat(mail.subject()).isEqualTo("Your request to join was approved");
        assertThat(mail.text()).contains("Volley Club").contains("activation link").doesNotContain("Rita Costa");
    }

    @Test
    void aRejectedApplicantIsToldAtTheAddressOfTheirRequest() {
        // Arrange
        JoinRequest request = JoinRequestFactory.create(association.id(),
                ContactDetails.of("Rita Costa", EmailAddress.of("rita@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), CLOCK);
        when(joinRequests.findById(association.id(), request.id())).thenReturn(Optional.of(request));

        // Act
        notifier.joinRequestRejected(association.id(), request.id());

        // Assert
        SmtpTestServer.Received mail = smtp.awaitMessages(1).get(0);
        assertThat(mail.to()).containsExactly("rita@example.com");
        assertThat(mail.subject()).isEqualTo("Your request to join was not approved");
        assertThat(mail.text()).contains("Volley Club").doesNotContain("Rita Costa");
    }

    @Test
    void aHostileAssociationNameIsEscapedInTheHtmlAndInTheHeadersOfEveryNotice() throws Exception {
        // Arrange
        Association hostile = AssociationFactory.create(HOSTILE_NAME, "evil-club", null, "Lisbon", "info@club.example", List.of("Beginner"));
        lenient().when(associations.findById(hostile.id())).thenReturn(Optional.of(hostile));
        Member ana = MemberFactory.create(hostile, ContactDetails.of("<b>Ana</b>", EmailAddress.of("ana@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), Set.of(MemberRole.MEMBER), CLOCK);
        Member boss = MemberFactory.create(hostile, ContactDetails.of("<i>Boss</i>", EmailAddress.of("boss@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), Set.of(MemberRole.ADMIN), CLOCK);
        Session session = SessionFactory.create(hostile.id(), TrainingGroupId.generate(), boss.id(), SESSION_START,
                SESSION_START.plus(Duration.ofMinutes(90)), 12);
        JoinRequest request = JoinRequestFactory.create(hostile.id(), ContactDetails.of("Rita", EmailAddress.of("rita@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), CLOCK);
        lenient().when(members.findById(hostile.id(), ana.id())).thenReturn(Optional.of(ana));
        lenient().when(sessions.findById(hostile.id(), session.id())).thenReturn(Optional.of(session));
        lenient().when(joinRequests.findById(hostile.id(), request.id())).thenReturn(Optional.of(request));
        when(members.findActiveAdminIds(hostile.id())).thenReturn(List.of(boss.id()));
        when(members.findByIds(any(), any())).thenReturn(List.of(boss));

        // Act
        notifier.bookingPromoted(hostile.id(), session.id(), ana.id());
        notifier.sessionCancelled(hostile.id(), session.id(), ana.id(), "<script>alert('x')</script>");
        notifier.noShowLimitReached(hostile.id(), ana.id(), 3);
        notifier.memberApproved(hostile.id(), ana.id());
        notifier.joinRequestRejected(hostile.id(), request.id());

        // Assert
        List<SmtpTestServer.Received> mails = smtp.awaitMessages(6);
        assertThat(mails).allSatisfy(mail -> {
            assertThat(mail.html()).doesNotContain("<img").doesNotContain("<script").doesNotContain("<b>").doesNotContain("<i>");
            assertThat(mail.subject()).doesNotContain("Club").doesNotContain("<");
        });
        assertThat(mails).anySatisfy(mail -> assertThat(mail.html()).contains("&lt;img src=x onerror=alert(1)&gt;Club &amp; &quot;Sons&quot;"));
        assertThat(mails).anySatisfy(mail -> assertThat(mail.html()).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertThat(mails).anySatisfy(mail -> assertThat(mail.html()).contains("&lt;b&gt;Ana&lt;/b&gt;"));
    }

    @Test
    void aMemberOfAnotherAssociationIsNeverMailed() throws Exception {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Session session = session();
        AssociationId other = AssociationId.generate();
        lenient().when(members.findById(other, ana.id())).thenReturn(Optional.empty());
        lenient().when(sessions.findById(other, session.id())).thenReturn(Optional.empty());

        // Act
        notifier.bookingPromoted(other, session.id(), ana.id());
        notifier.memberApproved(other, ana.id());
        awaitIdle();

        // Assert
        assertThat(smtp.received()).isEmpty();
    }

    @Test
    void nobodyIsMailedWhoIsInactiveOrErased() throws Exception {
        // Arrange
        Member gone = member("Gone Away", "gone@example.com").deactivate();
        lenient().when(members.findById(association.id(), gone.id())).thenReturn(Optional.of(gone));
        Member erased = member("Erased Person", "erased@example.com").anonymise(CLOCK);
        lenient().when(members.findById(association.id(), erased.id())).thenReturn(Optional.of(erased));
        JoinRequest erasedRequest = JoinRequestFactory.create(association.id(),
                ContactDetails.of("Rita", EmailAddress.of("rita@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), CLOCK).anonymise(CLOCK);
        when(joinRequests.findById(association.id(), erasedRequest.id())).thenReturn(Optional.of(erasedRequest));
        Session session = session();

        // Act
        notifier.sessionCancelled(association.id(), session.id(), gone.id(), "Closed");
        notifier.sessionCancelled(association.id(), session.id(), erased.id(), "Closed");
        notifier.joinRequestRejected(association.id(), erasedRequest.id());
        awaitIdle();

        // Assert
        assertThat(smtp.received()).isEmpty();
    }

    @Test
    void aLookupThatFailsIsRetriedAndTheMailArrivesOnce() throws Exception {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Session session = session();
        AtomicInteger lookups = new AtomicInteger();
        when(sessions.findById(association.id(), session.id())).thenAnswer(call -> {
            if (lookups.incrementAndGet() == 1) {
                throw new IllegalStateException("database unavailable");
            }
            return Optional.of(session);
        });

        // Act
        notifier.bookingPromoted(association.id(), session.id(), ana.id());

        // Assert
        assertThat(smtp.awaitMessages(1)).hasSize(1);
        awaitIdle();
        assertThat(smtp.received()).hasSize(1);
        assertThat(lookups).hasValue(2);
    }

    @Test
    void oneAdministratorsFailingMailboxNeverRepeatsTheOthersMail() throws Exception {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Member boss = member("Boss One", "boss@example.com", MemberRole.ADMIN);
        Member deputy = member("Deputy Two", "deputy@example.com", MemberRole.ADMIN);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(boss.id(), deputy.id()));
        when(members.findByIds(any(), any())).thenReturn(List.of(boss, deputy));
        JavaMailSenderImpl picky = new JavaMailSenderImpl() {
            @Override
            protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
                try {
                    if (messages[0].getAllRecipients()[0].toString().equals("boss@example.com")) {
                        throw new MailSendException("550 mailbox full");
                    }
                } catch (jakarta.mail.MessagingException e) {
                    throw new IllegalStateException(e);
                }
                super.doSend(messages, originalMessages);
            }
        };
        picky.setHost("127.0.0.1");
        picky.setPort(smtp.port());

        // Act
        notifierOver(picky).noShowLimitReached(association.id(), ana.id(), 3);

        // Assert
        smtp.awaitMessages(2);
        awaitIdle();
        assertThat(smtp.received()).extracting(mail -> mail.to().get(0))
                .containsExactlyInAnyOrder("ana.silva@example.com", "deputy@example.com");
    }

    @Test
    void aFailureWhileLookingUpTheAdministratorsNeverMailsTheMemberTwice() throws Exception {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Member boss = member("Boss One", "boss@example.com", MemberRole.ADMIN);
        AtomicInteger lookups = new AtomicInteger();
        when(members.findActiveAdminIds(association.id())).thenAnswer(call -> {
            if (lookups.incrementAndGet() == 1) {
                throw new IllegalStateException("database unavailable");
            }
            return List.of(boss.id());
        });
        when(members.findByIds(any(), any())).thenReturn(List.of(boss));

        // Act
        notifier.noShowLimitReached(association.id(), ana.id(), 3);

        // Assert - the whole notice was retried, and nothing had been queued before the failure
        smtp.awaitMessages(2);
        awaitIdle();
        assertThat(smtp.received()).extracting(mail -> mail.to().get(0))
                .containsExactlyInAnyOrder("ana.silva@example.com", "boss@example.com");
        assertThat(lookups).hasValue(2);
    }

    @Test
    void noAddressNameReasonOrAssociationNameReachesTheLog() throws Exception {
        // Arrange
        Member ana = member("Ana Silva", "ana.silva@example.com");
        Member gone = member("Gone Away", "gone@example.com").deactivate();
        lenient().when(members.findById(association.id(), gone.id())).thenReturn(Optional.of(gone));
        Session session = session();
        when(sessions.findById(association.id(), session.id())).thenThrow(new IllegalStateException("cannot read ana.silva@example.com"))
                .thenReturn(Optional.of(session));
        try (LogCapture logs = new LogCapture()) {
            // Act
            notifier.bookingPromoted(association.id(), session.id(), ana.id());
            notifier.sessionCancelled(association.id(), session.id(), gone.id(), "Secret reason text");
            notifier.sessionCancelled(association.id(), session.id(), ana.id(), "Secret reason text");
            smtp.awaitMessages(2);
            awaitIdle();

            // Assert
            assertThat(logs.everything()).contains(association.id().toString(), ana.id().toString())
                    .doesNotContain("ana.silva").doesNotContain("Ana Silva").doesNotContain("Secret reason text")
                    .doesNotContain("Volley Club").doesNotContain("gone@example.com").doesNotContain("14/10/2026");
        }
    }
}
