package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListMembersQuery;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.result.MemberListEntry;
import com.regivolley.api.application.result.MemberListing;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** US-07, US-08: the administrator's member list, filtered, bounded and tenant-scoped. */
@ExtendWith(MockitoExtension.class)
class ListMembersServiceTest {

    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private MembershipStore memberships;

    private Association association;
    private Member admin;
    private ListMembersUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        useCase = new ListMembersService(members, associations, memberships);
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
    }

    @Test
    void listsTheMembersWithTheirLevelNameAndWhetherTheyActivatedTheirAccount() {
        // Arrange
        Member activated = Data.atLevel(Data.member(association), association, "Intermediate");
        Member pending = Data.member(association);
        when(members.search(association.id(), Optional.empty(), Optional.empty(), MemberRepository.MAX_LISTED + 1))
                .thenReturn(List.of(activated, pending));
        when(memberships.findConfirmedMemberIds(eq(association.id()), any())).thenReturn(Set.of(activated.id()));

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        assertThat(listing.truncated()).isFalse();
        assertThat(listing.members()).extracting(MemberListEntry::member).containsExactly(activated, pending);
        assertThat(listing.members()).extracting(MemberListEntry::levelName).containsExactly(Optional.of("Intermediate"), Optional.of("Beginner"));
        assertThat(listing.members()).extracting(MemberListEntry::accountActivated).containsExactly(true, false);
    }

    @Test
    void theActivationOfAllTheListedMembersIsReadInOneBatch() {
        // Arrange
        Member first = Data.member(association);
        Member second = Data.member(association);
        when(members.search(any(), any(), any(), anyInt())).thenReturn(List.of(first, second));
        when(memberships.findConfirmedMemberIds(any(), any())).thenReturn(Set.of());

        // Act
        useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        verify(memberships, times(1)).findConfirmedMemberIds(eq(association.id()), eq(List.of(first.id(), second.id())));
    }

    @Test
    void theStatusAndTheTrimmedTextAreHandedToTheSearch() {
        // Arrange
        when(members.search(association.id(), Optional.of(MemberStatus.INACTIVE), Optional.of("rita"), MemberRepository.MAX_LISTED + 1))
                .thenReturn(List.of());

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), MemberStatus.INACTIVE, "  rita  "));

        // Assert
        assertThat(listing.members()).isEmpty();
        verify(members).search(association.id(), Optional.of(MemberStatus.INACTIVE), Optional.of("rita"), MemberRepository.MAX_LISTED + 1);
    }

    @Test
    void blankTextMeansNoSearch() {
        // Arrange
        when(members.search(association.id(), Optional.empty(), Optional.empty(), MemberRepository.MAX_LISTED + 1)).thenReturn(List.of());

        // Act
        useCase.execute(new ListMembersQuery(Data.actor(admin), null, "   "));

        // Assert
        verify(members).search(association.id(), Optional.empty(), Optional.empty(), MemberRepository.MAX_LISTED + 1);
    }

    @Test
    void anEmptyResultReadsNoMembershipsAtAll() {
        // Arrange
        when(members.search(any(), any(), any(), anyInt())).thenReturn(List.of());

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), null, "nobody"));

        // Assert
        assertThat(listing.members()).isEmpty();
        assertThat(listing.truncated()).isFalse();
        verifyNoInteractions(memberships);
    }

    @Test
    void moreMatchesThanTheCapAreCutAndFlaggedAsTruncated() {
        // Arrange
        List<Member> found = IntStream.rangeClosed(1, MemberRepository.MAX_LISTED + 1).mapToObj(i -> Data.member(association)).toList();
        when(members.search(association.id(), Optional.empty(), Optional.empty(), MemberRepository.MAX_LISTED + 1)).thenReturn(found);
        when(memberships.findConfirmedMemberIds(any(), any())).thenReturn(Set.of());

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        assertThat(listing.truncated()).isTrue();
        assertThat(listing.members()).hasSize(MemberRepository.MAX_LISTED);
        assertThat(listing.members()).extracting(MemberListEntry::member).containsExactlyElementsOf(found.subList(0, MemberRepository.MAX_LISTED));
    }

    @Test
    void exactlyTheCapIsNotTruncated() {
        // Arrange
        List<Member> found = IntStream.rangeClosed(1, MemberRepository.MAX_LISTED).mapToObj(i -> Data.member(association)).toList();
        when(members.search(any(), any(), any(), anyInt())).thenReturn(found);
        when(memberships.findConfirmedMemberIds(any(), any())).thenReturn(Set.of());

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        assertThat(listing.truncated()).isFalse();
        assertThat(listing.members()).hasSize(MemberRepository.MAX_LISTED);
    }

    @Test
    void aLevelThatCannotBeFoundLeavesTheNameOutInsteadOfFailingTheList() {
        // Arrange
        Association other = Data.association();
        Member strayed = Data.member(other);
        when(members.search(any(), any(), any(), anyInt())).thenReturn(List.of(strayed));
        when(memberships.findConfirmedMemberIds(any(), any())).thenReturn(Set.of());

        // Act
        MemberListing listing = useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        assertThat(listing.members()).singleElement().satisfies(entry -> assertThat(entry.levelName()).isEmpty());
    }

    @Test
    void textLongerThanTheLimitIsRefusedAndNothingIsSearched() {
        // Arrange
        Executable act = () -> useCase.execute(new ListMembersQuery(Data.actor(admin), null, "x".repeat(ListMembersService.MAX_TEXT_LENGTH + 1)));

        // Act
        InvalidFieldException refused = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(refused.field()).isEqualTo("q");
        verify(members, never()).search(any(), any(), any(), anyInt());
    }

    @Test
    void aNonAdministratorIsNotAllowedAndNothingIsRead() {
        // Arrange
        Member coach = Data.coach(association);
        when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(coach));
        Executable act = () -> useCase.execute(new ListMembersQuery(Data.actor(coach), null, null));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).search(any(), any(), any(), anyInt());
        verifyNoInteractions(memberships);
    }

    @Test
    void aDeactivatedAdministratorIsNotAllowed() {
        // Arrange
        Member former = Data.admin(association).deactivate();
        when(members.findById(association.id(), former.id())).thenReturn(Optional.of(former));
        Executable act = () -> useCase.execute(new ListMembersQuery(Data.actor(former), null, null));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).search(any(), any(), any(), anyInt());
    }

    @Test
    void anActorWhoIsNotAMemberOfTheAssociationIsNotFound() {
        // Arrange
        Member stranger = Data.admin(Data.association());
        when(members.findById(stranger.associationId(), stranger.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(new ListMembersQuery(Data.actor(stranger), null, null));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(members, never()).search(any(), any(), any(), anyInt());
    }

    @Test
    void onlyTheActorsAssociationIsEverQueried() {
        // Arrange
        when(members.search(any(), any(), any(), anyInt())).thenReturn(List.of(Data.member(association)));
        when(memberships.findConfirmedMemberIds(any(), any())).thenReturn(Set.of());

        // Act
        useCase.execute(new ListMembersQuery(Data.actor(admin), null, null));

        // Assert
        verify(members).search(eq(association.id()), any(), any(), anyInt());
        verify(memberships).findConfirmedMemberIds(eq(association.id()), any(Collection.class));
        verify(associations).findById(association.id());
    }
}
