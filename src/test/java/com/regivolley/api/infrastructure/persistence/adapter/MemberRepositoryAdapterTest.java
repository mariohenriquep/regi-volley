package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class MemberRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MemberRepository members;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Member saveAndReload(Member member) {
        Member saved = members.save(member);
        flushAndClear();
        return members.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    @Test
    void aNewMemberComesBackWithContactConsentRolesAndLevel() {
        // Arrange
        Association association = newAssociation();
        Member member = MemberFactory.create(association, Fixtures.contact("Ana Silva"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER, MemberRole.COACH), CLOCK);

        // Act
        Member loaded = saveAndReload(member);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(member);
        assertThat(loaded.roles()).containsExactlyInAnyOrder(MemberRole.MEMBER, MemberRole.COACH);
        assertThat(loaded.levelId()).isEqualTo(association.entryLevelId());
        assertThat(loaded.joinedAt()).isEqualTo(Fixtures.NOW);
    }

    @Test
    void levelHistoryRolesAndStatusEditsArePersistedInOrder() {
        // Arrange
        Association association = newAssociation();
        Member stored = members.save(Fixtures.member(association, "Ana"));
        flushAndClear();
        MemberId coach = MemberId.generate();
        Member edited = members.findById(association.id(), stored.id()).orElseThrow()
                .changeLevel(association, association.levels().get(1).id(), coach, Fixtures.at(Fixtures.NOW.plusSeconds(60)))
                .changeLevel(association, association.levels().get(2).id(), coach, Fixtures.at(Fixtures.NOW.plusSeconds(120)))
                .grantRole(MemberRole.ADMIN)
                .deactivate();

        // Act
        Member loaded = saveAndReload(edited);

        // Assert
        assertThat(loaded).usingRecursiveComparison().ignoringFields("version").isEqualTo(edited);
        assertThat(loaded.version()).isEqualTo(1L);
        assertThat(loaded.levelChanges()).extracting(LevelChange::to)
                .containsExactly(association.levels().get(1).id(), association.levels().get(2).id());
        assertThat(loaded.roles()).containsExactlyInAnyOrder(MemberRole.MEMBER, MemberRole.ADMIN);
        assertThat(loaded.status()).isEqualTo(MemberStatus.INACTIVE);
    }

    @Test
    void anAnonymisedMemberKeepsItsPlaceholdersAndNoPhone() {
        // Arrange
        Association association = newAssociation();
        Member anonymised = Fixtures.member(association, "Ana").anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(10)));

        // Act
        Member loaded = saveAndReload(anonymised);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(anonymised);
        assertThat(loaded.isAnonymised()).isTrue();
        assertThat(loaded.phone()).isEmpty();
        assertThat(loaded.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
    }

    @Test
    void erasingAMemberOverwritesTheStoredPersonalData() {
        // Arrange
        Association association = newAssociation();
        Member stored = members.save(Fixtures.member(association, "Ana Silva"));
        flushAndClear();

        // Act
        Member erased = saveAndReload(members.findById(association.id(), stored.id()).orElseThrow()
                .anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(10))));

        // Assert
        assertThat(erased.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
        assertThat(erased.email()).isNotEqualTo(stored.email());
        assertThat(erased.roles()).containsExactly(MemberRole.MEMBER);
    }

    @Test
    void findByEmailIsScopedToTheAssociation() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        EmailAddress shared = EmailAddress.of("same.person@example.com");
        ContactDetails contact = ContactDetails.of("Same Person", shared, PhoneNumber.of("912345678"));
        Member inA = members.save(MemberFactory.create(a, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
        Member inB = members.save(MemberFactory.create(b, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
        flushAndClear();

        // Act
        Member foundInA = members.findByEmail(a.id(), shared).orElseThrow();
        Member foundInB = members.findByEmail(b.id(), shared).orElseThrow();

        // Assert
        assertThat(foundInA.id()).isEqualTo(inA.id());
        assertThat(foundInB.id()).isEqualTo(inB.id());
        assertThat(members.findByEmail(a.id(), EmailAddress.of("nobody@example.com"))).isEmpty();
    }

    @Test
    void aMemberIsInvisibleToAnotherAssociationByIdAndByEmail() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Member ofA = members.save(Fixtures.member(a, "Ana"));
        flushAndClear();

        // Act
        boolean byIdAsB = members.findById(b.id(), ofA.id()).isPresent();
        boolean byEmailAsB = members.findByEmail(b.id(), ofA.email()).isPresent();

        // Assert
        assertThat(byIdAsB).isFalse();
        assertThat(byEmailAsB).isFalse();
        assertThat(members.findById(a.id(), ofA.id())).isPresent();
    }

    @Test
    void theSameEmailTwiceInOneAssociationIsRejectedWithoutRevealingIt() {
        // Arrange
        Association association = newAssociation();
        ContactDetails contact = ContactDetails.of("Ana", EmailAddress.of("dup@example.com"), PhoneNumber.of("912345678"));
        members.save(MemberFactory.create(association, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
        Executable act = () -> members.save(
                MemberFactory.create(association, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));

        // Act
        MemberEmailAlreadyUsedException ex = assertThrows(MemberEmailAlreadyUsedException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("dup@example.com");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void changingAnotherMembersDetailsToATakenEmailIsRejectedToo() {
        // Arrange
        Association association = newAssociation();
        Member first = members.save(Fixtures.member(association, "Ana"));
        Member second = members.save(Fixtures.member(association, "Rui"));
        entityManager.flush();
        entityManager.clear();
        Member stolen = MemberFactory.reconstitute(second.id(), second.associationId(),
                ContactDetails.of("Rui", first.email(), PhoneNumber.of("912345678")), second.consent(),
                second.status(), second.levelId(), second.roles(), second.levelChanges(), second.joinedAt(), null, second.version());
        Executable act = () -> members.save(stolen);

        // Act
        MemberEmailAlreadyUsedException ex = assertThrows(MemberEmailAlreadyUsedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void aNewMemberStartsAtVersionZeroAndEverySaveMovesItByExactlyOne() {
        // Arrange
        Association association = newAssociation();
        Member stored = members.save(Fixtures.member(association, "Ana"));
        flushAndClear();

        // Act
        Member granted = members.save(members.findById(association.id(), stored.id()).orElseThrow().grantRole(MemberRole.COACH));
        flushAndClear();
        Member childOnly = members.save(granted.changeLevel(association, association.levels().get(1).id(),
                MemberId.generate(), Fixtures.at(Fixtures.NOW.plusSeconds(60))));
        flushAndClear();

        // Assert
        assertThat(stored.version()).isZero();
        assertThat(granted.version()).isEqualTo(1L);
        assertThat(childOnly.version()).isEqualTo(2L);
        assertThat(members.findById(association.id(), stored.id()).orElseThrow().version()).isEqualTo(2L);
    }

    @Test
    void aStaleCopyCanNeverWriteTheOldPersonalDataBackOverAnErasure() {
        // Arrange
        Association association = newAssociation();
        Member stored = members.save(Fixtures.member(association, "Ana Silva"));
        flushAndClear();
        Member staleEditor = members.findById(association.id(), stored.id()).orElseThrow();
        Member eraser = members.findById(association.id(), stored.id()).orElseThrow();
        members.save(eraser.anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(10))));
        flushAndClear();
        Executable act = () -> members.save(staleEditor.grantRole(MemberRole.COACH));

        // Act
        MemberModifiedConcurrentlyException ex = assertThrows(MemberModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(stored.id());
        Member current = members.findById(association.id(), stored.id()).orElseThrow();
        assertThat(current.isAnonymised()).isTrue();
        assertThat(current.name()).isEqualTo(ContactDetails.ANONYMISED_NAME);
        assertThat(current.email()).isNotEqualTo(stored.email());
    }

    @Test
    void findByIdForUpdateReturnsTheStoredMemberInItsOwnAssociationOnly() {
        // Arrange
        Association association = newAssociation();
        Association other = newAssociation();
        Member stored = members.save(MemberFactory.create(association, Fixtures.contact("Ana Silva"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER), CLOCK));
        flushAndClear();

        // Act
        var own = members.findByIdForUpdate(association.id(), stored.id());
        var asOther = members.findByIdForUpdate(other.id(), stored.id());

        // Assert
        assertThat(own).hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(stored.id()));
        assertThat(asOther).isEmpty();
    }

    @Test
    void aConstraintViolationMessageNeverContainsTheMembersPersonalData() {
        // Arrange
        Association association = newAssociation();
        String email = "secret.person@example.com";
        Executable notNull = () -> jdbc.update("""
                insert into members (id, association_id, name, email, phone, consent_given_at, consent_policy_version,
                                     status, level_id, joined_at, version)
                values (?, ?, NULL, ?, '912345678', now(), 'v1', 'ACTIVE', ?, now(), 0)""",
                java.util.UUID.randomUUID(), association.id().value(), email, association.entryLevelId().value());

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, notNull);

        // Assert
        assertThat(ex.getMessage()).contains("not-null").doesNotContain(email);
        assertThat(ex.getMostSpecificCause().getMessage()).doesNotContain(email);
    }

    @Test
    void findActiveAdminIdsReturnsOnlyTheActiveAdminsOfThatAssociationByIdOrder() {
        // Arrange
        Association association = newAssociation();
        Association other = newAssociation();
        Member admin = members.save(MemberFactory.create(association, Fixtures.contact("Admin One"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN, MemberRole.MEMBER), CLOCK));
        Member coachAdmin = members.save(MemberFactory.create(association, Fixtures.contact("Admin Two"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN, MemberRole.COACH), CLOCK));
        members.save(MemberFactory.create(association, Fixtures.contact("Gone Admin"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN), CLOCK).deactivate());
        members.save(MemberFactory.create(association, Fixtures.contact("Plain"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER), CLOCK));
        members.save(MemberFactory.create(other, Fixtures.contact("Foreign Admin"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN), CLOCK));
        flushAndClear();

        // Act
        var admins = members.findActiveAdminIds(association.id());

        // Assert
        assertThat(admins).containsExactlyInAnyOrder(admin.id(), coachAdmin.id());
        assertThat(admins).extracting(id -> id.value().toString()).isSorted();
        assertThat(members.findActiveAdminIds(other.id())).hasSize(1);
        assertThat(members.findActiveAdminIds(com.regivolley.api.domain.model.valueobject.AssociationId.generate())).isEmpty();
    }

    @Test
    void findByIdsReturnsTheRequestedMembersOfThatAssociationOnly() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Member one = members.save(Fixtures.member(a, "One"));
        Member two = members.save(Fixtures.member(a, "Two"));
        Member three = members.save(Fixtures.member(a, "Three"));
        Member foreign = members.save(Fixtures.member(b, "Foreign"));
        flushAndClear();

        // Act
        var found = members.findByIds(a.id(), java.util.List.of(one.id(), two.id(), foreign.id()));
        var asOther = members.findByIds(b.id(), java.util.List.of(one.id()));

        // Assert
        assertThat(found).extracting(Member::id).containsExactlyInAnyOrder(one.id(), two.id());
        assertThat(found).extracting(Member::id).doesNotContain(three.id(), foreign.id());
        assertThat(asOther).isEmpty();
        assertThat(members.findByIds(a.id(), java.util.List.of())).isEmpty();
    }

    private Member saved(Association association, String name, String email) {
        return members.save(MemberFactory.create(association,
                ContactDetails.of(name, EmailAddress.of(email), PhoneNumber.of("912345678")), Fixtures.consent(),
                Set.of(MemberRole.MEMBER), CLOCK));
    }

    @Test
    void searchListsTheAssociationsMembersByNameIgnoringCaseWithRolesAndLevelHistory() {
        // Arrange
        Association association = newAssociation();
        Member rui = saved(association, "rui Alves", "rui@example.com");
        Member ana = members.save(MemberFactory.create(association, Fixtures.contact("Ana Silva"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER, MemberRole.COACH), CLOCK)
                .changeLevel(association, association.levels().get(1).id(), rui.id(), CLOCK));
        Member bruno = saved(association, "Bruno Dias", "bruno@example.com");
        flushAndClear();

        // Act
        var found = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.empty(), 10);

        // Assert
        assertThat(found).extracting(Member::id).containsExactly(ana.id(), bruno.id(), rui.id());
        Member loadedAna = found.get(0);
        assertThat(loadedAna.roles()).containsExactlyInAnyOrder(MemberRole.MEMBER, MemberRole.COACH);
        assertThat(loadedAna.levelChanges()).hasSize(1);
        assertThat(loadedAna.levelId()).isEqualTo(association.levels().get(1).id());
    }

    @Test
    void searchFiltersByStatus() {
        // Arrange
        Association association = newAssociation();
        Member active = saved(association, "Active One", "active@example.com");
        Member inactive = members.save(MemberFactory.create(association, Fixtures.contact("Inactive One"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER), CLOCK).deactivate());
        flushAndClear();

        // Act
        var onlyActive = members.search(association.id(), java.util.Optional.of(MemberStatus.ACTIVE), java.util.Optional.empty(), 10);
        var onlyInactive = members.search(association.id(), java.util.Optional.of(MemberStatus.INACTIVE), java.util.Optional.empty(), 10);

        // Assert
        assertThat(onlyActive).extracting(Member::id).containsExactly(active.id());
        assertThat(onlyInactive).extracting(Member::id).containsExactly(inactive.id());
    }

    @Test
    void searchMatchesTheTextInTheNameOrTheEmailIgnoringCase() {
        // Arrange
        Association association = newAssociation();
        Member byName = saved(association, "Rita Costa", "x1@example.com");
        Member byEmail = saved(association, "Someone Else", "RITA.private@example.com");
        saved(association, "Paulo Dias", "paulo@example.com");
        flushAndClear();

        // Act
        var found = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.of("rItA"), 10);

        // Assert
        assertThat(found).extracting(Member::id).containsExactlyInAnyOrder(byName.id(), byEmail.id());
    }

    @Test
    void searchTreatsPercentUnderscoreAndBackslashAsOrdinaryCharacters() {
        // Arrange
        Association association = newAssociation();
        Member percent = saved(association, "Discount 100% Club", "a1@example.com");
        saved(association, "Discount 100 Club", "a2@example.com");
        Member underscore = saved(association, "Snake_case", "a3@example.com");
        saved(association, "SnakeXcase", "a4@example.com");
        Member backslash = saved(association, "Back\\slash", "a5@example.com");
        flushAndClear();

        // Act
        var percents = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.of("100%"), 10);
        var underscores = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.of("e_c"), 10);
        var backslashes = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.of("\\"), 10);
        var wildcard = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.of("%"), 10);

        // Assert
        assertThat(percents).extracting(Member::id).containsExactly(percent.id());
        assertThat(underscores).extracting(Member::id).containsExactly(underscore.id());
        assertThat(backslashes).extracting(Member::id).containsExactly(backslash.id());
        assertThat(wildcard).extracting(Member::id).containsExactly(percent.id());
    }

    @Test
    void searchLeavesAnonymisedMembersOut() {
        // Arrange
        Association association = newAssociation();
        Member kept = saved(association, "Kept Person", "kept@example.com");
        members.save(Fixtures.member(association, "Erased Person").anonymise(Fixtures.at(Fixtures.NOW.plusSeconds(10))));
        flushAndClear();

        // Act
        var all = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.empty(), 10);
        var byPlaceholder = members.search(association.id(), java.util.Optional.empty(),
                java.util.Optional.of(ContactDetails.ANONYMISED_NAME), 10);

        // Assert
        assertThat(all).extracting(Member::id).containsExactly(kept.id());
        assertThat(byPlaceholder).isEmpty();
    }

    @Test
    void searchReturnsAtMostTheLimitInNameOrder() {
        // Arrange
        Association association = newAssociation();
        Member a = saved(association, "A", "a@example.com");
        Member b = saved(association, "B", "b@example.com");
        saved(association, "C", "c@example.com");
        flushAndClear();

        // Act
        var found = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.empty(), 2);

        // Assert
        assertThat(found).extracting(Member::id).containsExactly(a.id(), b.id());
    }

    @Test
    void searchNeverReturnsAnotherAssociationsMembers() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Member ofA = saved(a, "Shared Name", "a@example.com");
        Member ofB = saved(b, "Shared Name", "b@example.com");
        flushAndClear();

        // Act
        var asA = members.search(a.id(), java.util.Optional.empty(), java.util.Optional.of("shared"), 10);
        var asB = members.search(b.id(), java.util.Optional.empty(), java.util.Optional.of("shared"), 10);
        var asNobody = members.search(com.regivolley.api.domain.model.valueobject.AssociationId.generate(), java.util.Optional.empty(),
                java.util.Optional.empty(), 10);

        // Assert
        assertThat(asA).extracting(Member::id).containsExactly(ofA.id());
        assertThat(asB).extracting(Member::id).containsExactly(ofB.id());
        assertThat(asNobody).isEmpty();
    }

    @Test
    void searchLoadsRolesAndLevelHistoryInBatchesNotOneQueryPerMember() {
        // Arrange
        Association association = newAssociation();
        for (int i = 0; i < 40; i++) {
            Member member = saved(association, "Member %02d".formatted(i), "m%02d@example.com".formatted(i));
            members.save(member.grantRole(MemberRole.COACH).changeLevel(association, association.levels().get(1).id(),
                    member.id(), Fixtures.at(Fixtures.NOW.plusSeconds(60))));
        }
        flushAndClear();
        org.hibernate.stat.Statistics statistics = entityManager.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // Act
        var found = members.search(association.id(), java.util.Optional.empty(), java.util.Optional.empty(), 100);
        long statements = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);

        // Assert - one query for the members and a few batched ones for the two collections, not 1 + 2 * 40
        assertThat(found).hasSize(40);
        assertThat(found).allSatisfy(member -> assertThat(member.levelChanges()).hasSize(1));
        assertThat(statements).isLessThanOrEqualTo(4L);
    }
}
