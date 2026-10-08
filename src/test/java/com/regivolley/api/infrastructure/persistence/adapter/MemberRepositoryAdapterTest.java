package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.MemberEmailAlreadyUsedException;
import com.regivolley.api.domain.exception.MemberModifiedConcurrentlyException;
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
        Member member = Member.create(association, Fixtures.contact("Ana Silva"), Fixtures.consent(),
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
        Member inA = members.save(Member.create(a, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
        Member inB = members.save(Member.create(b, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
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
        members.save(Member.create(association, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));
        Executable act = () -> members.save(
                Member.create(association, contact, Fixtures.consent(), Set.of(MemberRole.MEMBER), CLOCK));

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
        Member stolen = Member.reconstruct(second.id(), second.associationId(),
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
        Member stored = members.save(Member.create(association, Fixtures.contact("Ana Silva"), Fixtures.consent(),
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
        Member admin = members.save(Member.create(association, Fixtures.contact("Admin One"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN, MemberRole.MEMBER), CLOCK));
        Member coachAdmin = members.save(Member.create(association, Fixtures.contact("Admin Two"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN, MemberRole.COACH), CLOCK));
        members.save(Member.create(association, Fixtures.contact("Gone Admin"), Fixtures.consent(),
                Set.of(MemberRole.ADMIN), CLOCK).deactivate());
        members.save(Member.create(association, Fixtures.contact("Plain"), Fixtures.consent(),
                Set.of(MemberRole.MEMBER), CLOCK));
        members.save(Member.create(other, Fixtures.contact("Foreign Admin"), Fixtures.consent(),
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
}
