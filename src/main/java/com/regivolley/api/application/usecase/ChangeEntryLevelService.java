package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeEntryLevelCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/** US-03, RN-20. An administrator chooses the level new members start at; existing members keep theirs. */
@Service
public class ChangeEntryLevelService implements ChangeEntryLevelUseCase {

    private final LevelChanger levels;

    public ChangeEntryLevelService(AssociationRepository associations, MemberRepository members, TransactionRunner transactions) {
        this.levels = new LevelChanger(associations, members, transactions);
    }

    @Override
    public Association execute(ChangeEntryLevelCommand command) {
        return levels.change(command.actor(), association -> association.changeEntryLevel(command.levelId()));
    }
}
