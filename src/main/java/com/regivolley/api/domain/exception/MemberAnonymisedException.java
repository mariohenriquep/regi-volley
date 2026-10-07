package com.regivolley.api.domain.exception;

/** Thrown when someone tries to change a member whose data was erased (RGPD): erasure is final. */
public class MemberAnonymisedException extends BusinessRuleException {

    public MemberAnonymisedException() {
        super("An anonymised member cannot be changed");
    }
}
