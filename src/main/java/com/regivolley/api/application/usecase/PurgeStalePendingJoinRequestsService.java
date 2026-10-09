package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.JoinRequestPurgeReport;
import com.regivolley.api.domain.exception.JoinRequestModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.JoinRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * RGPD data minimisation. A join request holds a stranger's name, email, phone and consent; one that no administrator decided within
 * {@value #RETENTION_DAYS} days is erased the way the erasure design says ({@link JoinRequest#anonymise}): the contact details become
 * placeholders and the request is closed as withdrawn, keeping its id, the consent record and the dates. Run daily, tenant by tenant;
 * one association failing is logged by id and never stops the others, and a request decided at the same moment is simply left to that
 * decision (it is no longer pending, and the optimistic lock refuses the stale copy).
 */
@Service
public class PurgeStalePendingJoinRequestsService implements PurgeStalePendingJoinRequestsUseCase {

    static final int RETENTION_DAYS = 30;
    /** Requests erased per transaction; a larger backlog takes further batches in the same run. */
    static final int BATCH = 500;
    private static final int MAX_BATCHES_PER_ASSOCIATION = 20;
    private static final Logger LOG = LoggerFactory.getLogger(PurgeStalePendingJoinRequestsService.class);

    private final AssociationRepository associations;
    private final JoinRequestRepository joinRequests;
    private final TransactionRunner transactions;
    private final Clock clock;

    public PurgeStalePendingJoinRequestsService(AssociationRepository associations, JoinRequestRepository joinRequests,
                                                TransactionRunner transactions, Clock clock) {
        this.associations = associations;
        this.joinRequests = joinRequests;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public JoinRequestPurgeReport execute(PurgeStalePendingJoinRequestsCommand command) {
        Instant cutoff = clock.instant().minus(Duration.ofDays(RETENTION_DAYS));
        int processed = 0;
        int failed = 0;
        int anonymised = 0;
        for (AssociationId associationId : associations.findAllIds()) {
            try {
                anonymised += purge(associationId, cutoff);
                processed++;
            } catch (RuntimeException e) {
                failed++;
                LOG.error("Purging stale join requests failed for association {}: {}", associationId, e.getClass().getSimpleName());
            }
        }
        // Audit line: counts only.
        LOG.info("Stale join requests erased: associations={} failed={} requests={}", processed, failed, anonymised);
        return new JoinRequestPurgeReport(processed, failed, anonymised);
    }

    private int purge(AssociationId associationId, Instant cutoff) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_ASSOCIATION; batch++) {
            List<JoinRequest> stale = joinRequests.findPendingRequestedBefore(associationId, cutoff, BATCH);
            for (JoinRequest request : stale) {
                total += anonymise(request);
            }
            if (stale.size() < BATCH) {
                break;
            }
        }
        return total;
    }

    /** One request per transaction: a lost race rolls back only that request. */
    private int anonymise(JoinRequest request) {
        try {
            transactions.inNewTransaction(() -> joinRequests.save(request.anonymise(clock)));
            return 1;
        } catch (JoinRequestModifiedConcurrentlyException e) {
            LOG.info("Join request {} was decided while being erased; left to that decision", request.id());
            return 0;
        }
    }
}
