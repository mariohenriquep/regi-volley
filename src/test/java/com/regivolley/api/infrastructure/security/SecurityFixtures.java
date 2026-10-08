package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.factory.MemberFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/** Association, member and clock for the security tests. */
final class SecurityFixtures {

    static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private SecurityFixtures() {
    }

    static Association association() {
        return AssociationFactory.create("Club", "club-sec", null, "Lisbon", "info@club.example", List.of("Beginner"));
    }

    static Member active(Association association) {
        return MemberFactory.create(association,
                ContactDetails.of("Ana Silva", EmailAddress.of("ana.silva@example.com"), PhoneNumber.of("912345678")),
                GdprConsent.record(true, "2026-01", NOW), Set.of(MemberRole.MEMBER), CLOCK);
    }
}
