package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AddLevelCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/** US-03. An administrator adds a level as the most advanced one; names must stay unique. */
@Service
public class AddLevelService implements AddLevelUseCase {

    private final LevelChanger levels;

    public AddLevelService(AssociationRepository associations, MemberRepository members, TransactionRunner transactions) {
        this.levels = new LevelChanger(associations, members, transactions);
    }

    @Override
    public Association execute(AddLevelCommand command) {
        return levels.change(command.actor(), association -> association.addLevel(command.name()));
    }
}
