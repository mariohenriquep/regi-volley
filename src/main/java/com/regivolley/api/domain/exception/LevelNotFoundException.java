package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.LevelId;

/** Thrown when a level id doesn't belong to the association it is addressed to (maps to "not found"). */
public class LevelNotFoundException extends RuntimeException {

    private final LevelId levelId;

    public LevelNotFoundException(LevelId levelId) {
        super("Level not found in this association: " + levelId);
        this.levelId = levelId;
    }

    public LevelId levelId() {
        return levelId;
    }
}
