package com.reconengine.recon;

import com.reconengine.audit.AuditService;
import com.reconengine.auth.CurrentActor;
import com.reconengine.common.Errors;
import com.reconengine.config.AppProperties;
import com.reconengine.recon.batch.JobParams;
import com.reconengine.settlement.SettlementFile;
import com.reconengine.settlement.SettlementFileStatus;
import com.reconengine.settlement.SettlementIngestService;
import com.reconengine.settlement.SettlementLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunRepository runs;
    private final SettlementIngestService files;
    private final SettlementLineRepository settlementLines;
    private final RunStateService runState;
    private final JobOperator jobOperator;
    private final Job reconciliationJob;
    private final TaskExecutor taskExecutor;
    private final AuditService audit;
    private final CurrentActor currentActor;
    private final AppProperties properties;
    private final Clock clock;

    public ReconciliationService(ReconciliationRunRepository runs, SettlementIngestService files,
                                 SettlementLineRepository settlementLines, RunStateService runState,
                                 JobOperator jobOperator, Job reconciliationJob, TaskExecutor taskExecutor,
                                 AuditService audit, CurrentActor currentActor, AppProperties properties,
                                 Clock clock) {
        this.runs = runs;
        this.files = files;
        this.settlementLines = settlementLines;
        this.runState = runState;
        this.jobOperator = jobOperator;
        this.reconciliationJob = reconciliationJob;
        this.taskExecutor = taskExecutor;
        this.audit = audit;
        this.currentActor = currentActor;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Registers the run, then hands the matching itself to a background thread so the caller
     * is not held open for a file with thousands of lines. Progress is read back through
     * {@link #get(UUID)}.
     */
    @Transactional
    public ReconciliationRun trigger(UUID fileId) {
        SettlementFile file = files.get(fileId);
        if (file.getStatus() != SettlementFileStatus.PARSED) {
            throw new Errors.Unprocessable("FILE_NOT_PARSED",
                    "File %s is in status %s and cannot be reconciled.".formatted(fileId, file.getStatus()));
        }

        SettlementLineRepository.DateRange range = settlementLines.findDateRange(fileId);
        if (range == null || range.getEarliest() == null) {
            throw new Errors.Unprocessable("FILE_HAS_NO_LINES", "File %s has no settlement lines.".formatted(fileId));
        }

        List<String> currencies = settlementLines.findCurrencies(fileId);
        CurrentActor.Actor actor = currentActor.require();

        ReconciliationRun run = new ReconciliationRun(fileId, actor.id(), clock.instant());
        try {
            runs.saveAndFlush(run);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index on (file_id) where status is active decided this, not a read.
            throw new Errors.Conflict("RUN_ALREADY_ACTIVE",
                    "A reconciliation run for file %s is already pending or running.".formatted(fileId));
        }

        audit.record(actor, "RECONCILIATION_RUN_TRIGGERED", "ReconciliationRun", run.getId(),
                Map.of("fileId", fileId.toString(), "filename", file.getFilename()));

        var window = properties.matching().dateWindow();
        JobParameters parameters = new JobParametersBuilder()
                .addString(JobParams.RUN_ID, run.getId().toString())
                .addString(JobParams.FILE_ID, fileId.toString())
                .addLong(JobParams.WINDOW_START_EPOCH, range.getEarliest().minus(window).toEpochMilli())
                .addLong(JobParams.WINDOW_END_EPOCH, range.getLatest().plus(window).toEpochMilli())
                .addString(JobParams.CURRENCIES, String.join(",", currencies))
                .toJobParameters();

        UUID runId = run.getId();
        // Launched only after this transaction commits: the job reads the run row back, and a
        // background thread would otherwise race the commit and not find it.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> launch(runId, parameters));
            }
        });

        return run;
    }

    private void launch(UUID runId, JobParameters parameters) {
        try {
            JobExecution execution = jobOperator.start(reconciliationJob, parameters);
            runState.markRunning(runId, execution.getId());

            if (execution.getStatus().isUnsuccessful()) {
                String failure = execution.getAllFailureExceptions().stream()
                        .findFirst()
                        .map(Throwable::toString)
                        .orElse("batch job finished with status " + execution.getStatus());
                runState.markFailed(runId, failure);
                log.error("reconciliation run {} failed: {}", runId, failure);
            }
        } catch (Exception ex) {
            log.error("reconciliation run {} could not be launched", runId, ex);
            runState.markFailed(runId, ex.toString());
        }
    }

    @Transactional(readOnly = true)
    public ReconciliationRun get(UUID runId) {
        return runs.findById(runId).orElseThrow(() -> new Errors.NotFound("Reconciliation run", runId));
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRun> list(Pageable pageable) {
        return runs.findAllByOrderByStartedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationRun> forFile(UUID fileId) {
        return runs.findByFileIdOrderByStartedAtDesc(fileId);
    }
}
