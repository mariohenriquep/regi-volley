package com.regivolley.api.domain.exception;

/** Thrown when someone tries to join without accepting the RGPD consent (US-05). */
public class ConsentRequiredException extends BusinessRuleException {

    public ConsentRequiredException() {
        super("The RGPD consent must be accepted to request membership");
    }
}
