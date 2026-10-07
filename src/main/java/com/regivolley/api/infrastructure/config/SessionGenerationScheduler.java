package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.command.GenerateSessionsForAllAssociationsCommand;
import com.regivolley.api.application.result.GenerationRunReport;
import com.regivolley.api.application.usecase.GenerateSessionsForAllAssociationsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The daily trigger of the session generation (US-10, RN-01). Switched on by
 * {@code regi-volley.session-generation.enabled=true} (the default of {@code application.yml}); the tests
 * switch it off. The cron runs in Europe/Lisbon, 03:00 unless
 * {@code regi-volley.session-generation.cron} says otherwise. The run is idempotent, so a missed or a
 * doubled run is harmless.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "regi-volley.session-generation.enabled", havingValue = "true")
public class SessionGenerationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SessionGenerationScheduler.class);

    private final GenerateSessionsForAllAssociationsUseCase generateAll;

    public SessionGenerationScheduler(GenerateSessionsForAllAssociationsUseCase generateAll) {
        this.generateAll = generateAll;
    }

    @Scheduled(cron = "${regi-volley.session-generation.cron:0 0 3 * * *}", zone = "Europe/Lisbon")
    public void generateSessions() {
        try {
            GenerationRunReport report = generateAll.execute(new GenerateSessionsForAllAssociationsCommand());
            LOG.info("Session generation done: {} associations, {} failed, {} sessions created",
                    report.associationsProcessed(), report.associationsFailed(), report.sessionsCreated());
        } catch (RuntimeException e) {
            LOG.error("Session generation run failed: {}", e.getClass().getSimpleName());
        }
    }
}
