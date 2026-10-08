package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RenameLevelCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/** US-03. An administrator renames a level; a level of another association is not found. */
@Service
public class RenameLevelService implements RenameLevelUseCase {

    private final LevelChanger levels;

    public RenameLevelService(AssociationRepository associations, MemberRepository members, TransactionRunner transactions) {
        this.levels = new LevelChanger(associations, members, transactions);
    }

    @Override
    public Association execute(RenameLevelCommand command) {
        return levels.change(command.actor(), association -> association.renameLevel(command.levelId(), command.newName()));
    }
}
