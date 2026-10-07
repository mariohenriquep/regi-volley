package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/**
 * What a person can do inside one association (personas and permission matrix in the
 * requirements). A person may hold several, e.g. COACH and MEMBER. Authorisation itself is
 * enforced by the infrastructure on every request (architecture.md section 11); the domain only
 * records the roles.
 */
public enum MemberRole implements ValueObject {
    MEMBER,
    COACH,
    ADMIN
}
