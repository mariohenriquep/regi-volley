package com.regivolley.api.infrastructure.web.dto;

import java.util.UUID;

/** The approved request and the id of the member it became (the activation link is mailed to the applicant). */
public record JoinRequestApprovalResponse(JoinRequestResponse request, UUID memberId) {
}
