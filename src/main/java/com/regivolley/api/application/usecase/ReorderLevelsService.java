package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ReorderLevelsCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import org.springframework.stereotype.Service;

/** US-03. An administrator sets the order of all the levels; the list must name every level exactly once. */
@Service
public class ReorderLevelsService implements ReorderLevelsUseCase {

    private final LevelChanger levels;

    public ReorderLevelsService(AssociationRepository associations, MemberRepository members, TransactionRunner transactions) {
        this.levels = new LevelChanger(associations, members, transactions);
    }

    @Override
    public Association execute(ReorderLevelsCommand command) {
        return levels.change(command.actor(), association -> association.reorderLevels(command.orderedLevelIds()));
    }
}
