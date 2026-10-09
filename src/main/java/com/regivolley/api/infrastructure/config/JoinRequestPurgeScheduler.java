package com.regivolley.api.infrastructure.config;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.result.JoinRequestPurgeReport;
import com.regivolley.api.application.usecase.PurgeStalePendingJoinRequestsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The daily trigger of the erasure of stale join requests (RGPD data minimisation: a stranger's contact details are not kept for more
 * than 30 days awaiting a decision). Switched on by {@code regi-volley.join-request-purge.enabled=true} (the default of
 * {@code application.yml}); the tests switch it off. The cron runs in Europe/Lisbon, 03:30 unless
 * {@code regi-volley.join-request-purge.cron} says otherwise. The run is idempotent, so a missed or a doubled run is harmless.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "regi-volley.join-request-purge.enabled", havingValue = "true")
public class JoinRequestPurgeScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(JoinRequestPurgeScheduler.class);

    private final PurgeStalePendingJoinRequestsUseCase purge;

    public JoinRequestPurgeScheduler(PurgeStalePendingJoinRequestsUseCase purge) {
        this.purge = purge;
    }

    @Scheduled(cron = "${regi-volley.join-request-purge.cron:0 30 3 * * *}", zone = "Europe/Lisbon")
    public void purgeStaleJoinRequests() {
        try {
            JoinRequestPurgeReport report = purge.execute(new PurgeStalePendingJoinRequestsCommand());
            LOG.info("Join request purge done: {} associations, {} failed, {} requests erased", report.associationsProcessed(),
                    report.associationsFailed(), report.requestsAnonymised());
        } catch (RuntimeException e) {
            LOG.error("Join request purge run failed: {}", e.getClass().getSimpleName());
        }
    }
}
