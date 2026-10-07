package com.regivolley.api.domain.model;

/**
 * Whether a member may book at all (RN-06). A placeholder input for booking eligibility: the
 * Member aggregate (issue #17) owns the real status and may replace or move this type.
 */
public enum MemberStatus {
    ACTIVE,
    INACTIVE
}
