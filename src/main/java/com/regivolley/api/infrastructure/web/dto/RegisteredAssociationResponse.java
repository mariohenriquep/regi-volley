package com.regivolley.api.infrastructure.web.dto;

/**
 * The answer to registering an association. It echoes the short name the visitor chose and nothing generated or looked up (no ids),
 * so the body is the same whatever the founder's email already was to the system (threat model D-10): the founder signs in through
 * the activation link mailed to them.
 */
public record RegisteredAssociationResponse(String shortName) {
}
