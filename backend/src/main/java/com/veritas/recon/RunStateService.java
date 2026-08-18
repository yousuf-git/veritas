package com.veritas.recon;

import com.veritas.common.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Status transitions live in their own bean so they commit independently of the batch job's
 * own transactions — marking a run FAILED must survive the rollback that caused the failure.
 */
@Service
public class RunStateService {

    private final ReconciliationRunRepository runs;
    private final Clock clock;

    public RunStateService(ReconciliationRunRepository runs, Clock clock) {
        this.runs = runs;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(UUID runId, Long batchJobExecutionId) {
        ReconciliationRun run = require(runId);
        run.markRunning(batchJobExecutionId);
        runs.save(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID runId, String error) {
        ReconciliationRun run = require(runId);
        run.markFailed(error, clock.instant());
        runs.save(run);
    }

    private ReconciliationRun require(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new Errors.NotFound("Reconciliation run", runId));
    }
}
