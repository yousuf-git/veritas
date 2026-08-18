package com.veritas.recon.batch;

import com.veritas.common.Errors;
import com.veritas.recon.DiscrepancyRepository;
import com.veritas.recon.MatchResultRepository;
import com.veritas.recon.MatchStage;
import com.veritas.recon.MatchStatus;
import com.veritas.recon.ReconciliationRun;
import com.veritas.recon.ReconciliationRunRepository;
import com.veritas.settlement.SettlementLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rolls the per-line verdicts up into the numbers the finance team actually reads. */
public class RunSummaryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(RunSummaryTasklet.class);

    private final ReconciliationRunRepository runs;
    private final MatchResultRepository matchResults;
    private final DiscrepancyRepository discrepancies;
    private final SettlementLineRepository settlementLines;
    private final Clock clock;
    private final UUID runId;
    private final UUID fileId;

    public RunSummaryTasklet(ReconciliationRunRepository runs, MatchResultRepository matchResults,
                             DiscrepancyRepository discrepancies, SettlementLineRepository settlementLines,
                             Clock clock, UUID runId, UUID fileId) {
        this.runs = runs;
        this.matchResults = matchResults;
        this.discrepancies = discrepancies;
        this.settlementLines = settlementLines;
        this.clock = clock;
        this.runId = runId;
        this.fileId = fileId;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        ReconciliationRun run = runs.findById(runId)
                .orElseThrow(() -> new Errors.NotFound("Reconciliation run", runId));

        List<String> currencies = settlementLines.findCurrencies(fileId);

        ReconciliationRun.Summary summary = new ReconciliationRun.Summary(
                matchResults.countByRunId(runId),
                matchResults.countByRunIdAndMatchStage(runId, MatchStage.EXACT),
                matchResults.countByRunIdAndMatchStage(runId, MatchStage.HEURISTIC),
                matchResults.countByRunIdAndMatchStatus(runId, MatchStatus.UNMATCHED),
                discrepancies.countByRunId(runId),
                matchResults.sumLineAmountsByStatus(runId, Set.of(MatchStatus.MATCHED, MatchStatus.PARTIAL)),
                matchResults.sumLineAmountsByStatus(runId, Set.of(MatchStatus.UNMATCHED)),
                // Only meaningful for a single-currency file; mixed files report per-currency in the report.
                currencies.size() == 1 ? currencies.getFirst() : null);

        run.markCompleted(summary, clock.instant());
        runs.save(run);

        log.info("run {} completed: {} lines, {} exact, {} heuristic, {} unmatched, {} discrepancies ({}% matched)",
                runId, summary.totalLines(), summary.matchedExact(), summary.matchedHeuristic(),
                summary.unmatched(), summary.discrepancyCount(), Math.round(run.matchRate() * 100));

        return RepeatStatus.FINISHED;
    }
}
