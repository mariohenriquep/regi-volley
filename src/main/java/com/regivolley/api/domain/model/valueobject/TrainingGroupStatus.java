package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** ACTIVE groups keep generating sessions; ARCHIVED ones never do again (archiving is final in Phase 1). */
public enum TrainingGroupStatus implements ValueObject {
    ACTIVE,
    ARCHIVED
}
