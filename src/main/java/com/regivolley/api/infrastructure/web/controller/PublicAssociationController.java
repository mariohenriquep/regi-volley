package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.GetPublicAssociationUseCase;
import com.regivolley.api.application.usecase.RegisterAssociationUseCase;
import com.regivolley.api.application.usecase.SubmitJoinRequestUseCase;
import com.regivolley.api.infrastructure.web.dto.AcknowledgementResponse;
import com.regivolley.api.infrastructure.web.dto.JoinAssociationRequest;
import com.regivolley.api.infrastructure.web.dto.PublicAssociationResponse;
import com.regivolley.api.infrastructure.web.dto.RegisterAssociationRequest;
import com.regivolley.api.infrastructure.web.dto.RegisteredAssociationResponse;
import com.regivolley.api.infrastructure.web.mapper.PublicWebMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The endpoints a visitor may call without an account (threat model section 7, rule 1): the public page (US-24), registering an
 * association (US-01) and asking to join one (US-05). The association is chosen by its public short name, the one place a client
 * supplies a tenant. The answers are uniform where a difference would tell a stranger something (D-10, D-14): registering echoes
 * only the short name, and a join request is {@code 202 RECEIVED} whether it was filed, the address is already a member or a
 * request is already waiting (the use case refuses the last two with one exception, which the advice turns into the same answer);
 * an unknown short name is a 404, as it is public.
 */
@RestController
@RequestMapping("/api/v1/public/associations")
public class PublicAssociationController {

    private final GetPublicAssociationUseCase getPublicAssociation;
    private final RegisterAssociationUseCase registerAssociation;
    private final SubmitJoinRequestUseCase submitJoinRequest;

    public PublicAssociationController(GetPublicAssociationUseCase getPublicAssociation, RegisterAssociationUseCase registerAssociation,
                                       SubmitJoinRequestUseCase submitJoinRequest) {
        this.getPublicAssociation = getPublicAssociation;
        this.registerAssociation = registerAssociation;
        this.submitJoinRequest = submitJoinRequest;
    }

    @GetMapping("/{shortName}")
    public PublicAssociationResponse page(@PathVariable String shortName) {
        return PublicWebMapper.toResponse(getPublicAssociation.execute(PublicWebMapper.toQuery(shortName)));
    }

    /** {@code 201} with the page of the new association in {@code Location}; the body echoes only the short name. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredAssociationResponse register(@Valid @RequestBody RegisterAssociationRequest body, HttpServletResponse response) {
        registerAssociation.execute(PublicWebMapper.toCommand(body));
        RegisteredAssociationResponse registered = PublicWebMapper.toRegisteredResponse(body);
        response.setHeader(HttpHeaders.LOCATION, "/api/v1/public/associations/" + registered.shortName());
        return registered;
    }

    @PostMapping("/{shortName}/join-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AcknowledgementResponse join(@PathVariable String shortName, @Valid @RequestBody JoinAssociationRequest body) {
        submitJoinRequest.execute(PublicWebMapper.toCommand(shortName, body));
        return AcknowledgementResponse.received();
    }
}
