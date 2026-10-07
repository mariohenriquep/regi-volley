package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.command.GenerateSessionsForAllAssociationsCommand;
import com.regivolley.api.application.result.GenerationRunReport;
import com.regivolley.api.application.result.SessionGenerationReport;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The daily job of US-10: runs {@link GenerateSessionsUseCase} for each association in turn, tenant by
 * tenant. One association failing is logged (by id) and counted, and never stops the others.
 */
@Service
public class GenerateSessionsForAllAssociationsService implements GenerateSessionsForAllAssociationsUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GenerateSessionsForAllAssociationsService.class);

    private final AssociationRepository associations;
    private final GenerateSessionsUseCase generateOne;

    public GenerateSessionsForAllAssociationsService(AssociationRepository associations, GenerateSessionsUseCase generateOne) {
        this.associations = associations;
        this.generateOne = generateOne;
    }

    @Override
    public GenerationRunReport execute(GenerateSessionsForAllAssociationsCommand command) {
        int processed = 0;
        int failed = 0;
        int created = 0;
        for (AssociationId associationId : associations.findAllIds()) {
            try {
                SessionGenerationReport report = generateOne.execute(new GenerateSessionsCommand(associationId));
                processed++;
                created += report.sessionsCreated();
            } catch (RuntimeException e) {
                failed++;
                LOG.error("Session generation failed for association {}: {}", associationId, e.getClass().getSimpleName());
            }
        }
        return new GenerationRunReport(processed, failed, created);
    }
}
