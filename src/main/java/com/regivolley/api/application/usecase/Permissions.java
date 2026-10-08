package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.MemberRole;

/**
 * Who may do what, decided from the actor's {@link Member} (roles are association data, architecture.md
 * section 11) and their relationship to the session, never from the transport. The infrastructure
 * has already authenticated the actor and resolved their tenant; these are the rules that need the data.
 * A deactivated member holds no powers, whatever roles they were left with.
 */
final class Permissions {

    private Permissions() {
    }

    /** An active administrator of the association. */
    static boolean isAdmin(Member actor) {
        return actor.isActive() && actor.hasRole(MemberRole.ADMIN);
    }

    /** An administrator, or the coach of this very session (the session's coach, who still holds the COACH role). */
    static boolean isStaffOf(Member actor, Session session) {
        return isAdmin(actor)
                || (actor.isActive() && actor.hasRole(MemberRole.COACH) && actor.id().equals(session.coachId()));
    }

    static void requireAdmin(Member actor, String action) {
        if (!isAdmin(actor)) {
            throw new NotAllowedException(action);
        }
    }

    static void requireStaffOf(Member actor, Session session, String action) {
        if (!isStaffOf(actor, session)) {
            throw new NotAllowedException(action);
        }
    }
}
