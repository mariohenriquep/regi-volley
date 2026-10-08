package com.regivolley.api.infrastructure.web.dto;

/** The answer when a request was received and nothing more may be said (threat model D-14: join requests). */
public record AcknowledgementResponse(String status) {

    public static AcknowledgementResponse received() {
        return new AcknowledgementResponse("RECEIVED");
    }
}
