package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListMembersQuery;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.result.MemberListEntry;
import com.regivolley.api.application.result.MemberListing;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-07, US-08. An administrator lists the members of their association, by name, optionally only those in one status and only those
 * whose name or email contains a text (ignoring case). Members who were anonymised (RGPD erasure) are not listed: they hold no data
 * to show and nothing about them can be changed. The list holds at most {@link MemberRepository#MAX_LISTED} members and says so
 * when it was cut. The level names come from the association's one load, and whether each member has activated their account from
 * one batch read of the memberships (no query per row). Contact data is shown to the administrator only. Read only, so no
 * transaction of its own.
 */
@Service
public class ListMembersService implements ListMembersUseCase {

    /** The longest search text accepted. */
    static final int MAX_TEXT_LENGTH = 100;

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final MembershipStore memberships;

    public ListMembersService(MemberRepository members, AssociationRepository associations, MembershipStore memberships) {
        this.members = members;
        this.associations = associations;
        this.memberships = memberships;
    }

    @Override
    public MemberListing execute(ListMembersQuery query) {
        AssociationId associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "list members");
        Optional<String> text = searchText(query.text());

        List<Member> found = members.search(associationId, Optional.ofNullable(query.status()), text, MemberRepository.MAX_LISTED + 1);
        boolean truncated = found.size() > MemberRepository.MAX_LISTED;
        List<Member> shown = truncated ? found.subList(0, MemberRepository.MAX_LISTED) : found;
        if (shown.isEmpty()) {
            return new MemberListing(List.of(), false);
        }

        Map<LevelId, String> levelNames = Lookups.association(associations, associationId).levels().stream()
                .collect(Collectors.toMap(Level::id, Level::name));
        List<MemberId> ids = shown.stream().map(Member::id).toList();
        Set<MemberId> activated = memberships.findConfirmedMemberIds(associationId, ids);
        return new MemberListing(shown.stream()
                .map(member -> new MemberListEntry(member, Optional.ofNullable(levelNames.get(member.levelId())), activated.contains(member.id())))
                .toList(), truncated);
    }

    /** The text to search for: trimmed, empty when there is none, refused when too long. */
    private static Optional<String> searchText(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new InvalidFieldException("q", "The search text must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return Optional.of(trimmed);
    }
}
