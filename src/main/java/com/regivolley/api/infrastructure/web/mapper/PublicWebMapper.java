package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.GetPublicAssociationQuery;
import com.regivolley.api.application.command.RegisterAssociationCommand;
import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.PublicAssociationPage;
import com.regivolley.api.application.result.PublicGroup;
import com.regivolley.api.application.result.PublicLevel;
import com.regivolley.api.application.result.PublicSlot;
import com.regivolley.api.application.result.PublicVenue;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.infrastructure.web.dto.JoinAssociationRequest;
import com.regivolley.api.infrastructure.web.dto.PublicAssociationResponse;
import com.regivolley.api.infrastructure.web.dto.RegisterAssociationRequest;
import com.regivolley.api.infrastructure.web.dto.RegisteredAssociationResponse;

/** The visitor-facing endpoints: the public page, registering an association and asking to join one. No caller, no tenant from the client. */
public final class PublicWebMapper {

    private PublicWebMapper() {
    }

    public static GetPublicAssociationQuery toQuery(String shortName) {
        return new GetPublicAssociationQuery(shortName);
    }

    public static RegisterAssociationCommand toCommand(RegisterAssociationRequest body) {
        return new RegisterAssociationCommand(body.name(), body.shortName(), body.nif(), body.locality(), body.contactEmail(),
                body.levelNames(), body.founderName(), body.founderEmail(), body.founderPhone(), body.consentAccepted(),
                body.policyVersion());
    }

    public static SubmitJoinRequestCommand toCommand(String shortName, JoinAssociationRequest body) {
        return new SubmitJoinRequestCommand(shortName, body.name(), body.email(), body.phone(), body.consentAccepted(),
                body.policyVersion());
    }

    /** Echoes the short name the visitor typed, normalised by the domain's own rule ({@link ShortName}); nothing generated. */
    public static RegisteredAssociationResponse toRegisteredResponse(RegisterAssociationRequest body) {
        return new RegisteredAssociationResponse(ShortName.of(body.shortName()).value());
    }

    public static PublicAssociationResponse toResponse(PublicAssociationPage page) {
        return new PublicAssociationResponse(page.name(), page.shortName(), page.locality(), page.contactEmail(),
                page.levels().stream().map(PublicLevel::name).toList(),
                page.groups().stream().map(PublicWebMapper::toGroup).toList(),
                page.venues().stream().map(PublicWebMapper::toVenue).toList());
    }

    private static PublicAssociationResponse.GroupView toGroup(PublicGroup group) {
        return new PublicAssociationResponse.GroupView(group.name(), group.levelNames(), group.venueName(),
                group.schedule().stream().map(PublicWebMapper::toSlot).toList());
    }

    private static PublicAssociationResponse.SlotView toSlot(PublicSlot slot) {
        return new PublicAssociationResponse.SlotView(slot.dayOfWeek().name(), slot.startTime().toString(), slot.durationMinutes());
    }

    private static PublicAssociationResponse.VenueView toVenue(PublicVenue venue) {
        return new PublicAssociationResponse.VenueView(venue.name(), venue.address(), venue.courts());
    }
}
