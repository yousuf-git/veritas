package com.veritas.recon.batch;

import com.veritas.recon.Discrepancy;
import com.veritas.recon.DiscrepancyRepository;
import com.veritas.recon.MatchResult;
import com.veritas.recon.MatchResultRepository;
import com.veritas.recon.matching.LineOutcome;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Writes a chunk of verdicts. Match results are flushed first because a discrepancy points back
 * at the result that produced it, and that identifier only exists once the result is persisted.
 */
public class MatchResultWriter implements ItemWriter<LineOutcome> {

    private final MatchResultRepository matchResults;
    private final DiscrepancyRepository discrepancies;
    private final UUID runId;

    public MatchResultWriter(MatchResultRepository matchResults, DiscrepancyRepository discrepancies, UUID runId) {
        this.matchResults = matchResults;
        this.discrepancies = discrepancies;
        this.runId = runId;
    }

    @Override
    public void write(Chunk<? extends LineOutcome> chunk) {
        List<MatchResult> results = chunk.getItems().stream().map(LineOutcome::result).toList();
        matchResults.saveAllAndFlush(results);

        List<Discrepancy> pending = new ArrayList<>();
        for (LineOutcome outcome : chunk.getItems()) {
            for (LineOutcome.PendingDiscrepancy discrepancy : outcome.discrepancies()) {
                pending.add(new Discrepancy(
                        runId,
                        outcome.result().getId(),
                        discrepancy.settlementLineId(),
                        discrepancy.ledgerEntryId(),
                        discrepancy.type(),
                        discrepancy.severity(),
                        discrepancy.amountImpact(),
                        discrepancy.detail()));
            }
        }

        if (!pending.isEmpty()) {
            discrepancies.saveAll(pending);
        }
    }
}
