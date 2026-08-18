package com.veritas.report;

import com.veritas.common.Money;
import com.veritas.recon.DiscrepancyRepository;
import com.veritas.recon.MatchResultRepository;
import com.veritas.recon.MatchStatus;
import com.veritas.recon.ReconciliationRun;
import com.veritas.recon.ReconciliationService;
import com.veritas.settlement.SettlementFile;
import com.veritas.settlement.SettlementIngestService;
import com.veritas.settlement.SettlementLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a run's per-line verdicts into the summary a finance lead signs off on: how much of the
 * file was accounted for, how much is still outstanding, and what the outstanding money is.
 */
@Service
public class ReportService {

    private final ReconciliationService runs;
    private final SettlementIngestService files;
    private final MatchResultRepository matchResults;
    private final DiscrepancyRepository discrepancies;

    public ReportService(ReconciliationService runs, SettlementIngestService files,
                         MatchResultRepository matchResults, DiscrepancyRepository discrepancies) {
        this.runs = runs;
        this.files = files;
        this.matchResults = matchResults;
        this.discrepancies = discrepancies;
    }

    @Transactional(readOnly = true)
    public ReportDtos.RunReport report(UUID runId) {
        ReconciliationRun run = runs.get(runId);
        SettlementFile file = files.get(run.getFileId());

        String currency = run.getCurrency() == null ? "USD" : run.getCurrency();

        List<ReportDtos.DiscrepancyBreakdown> breakdown = discrepancies.breakdownForRun(runId).stream()
                .map(row -> new ReportDtos.DiscrepancyBreakdown(
                        row.getType(),
                        row.getSeverity(),
                        row.getCount(),
                        row.getAmountMinor(),
                        Money.of(row.getAmountMinor(), currency).toMajorUnits()))
                .toList();

        long outstandingMinor = breakdown.stream()
                .mapToLong(ReportDtos.DiscrepancyBreakdown::amountAtRiskMinor)
                .sum();

        return new ReportDtos.RunReport(
                run.getId().toString(),
                file.getId().toString(),
                file.getFilename(),
                file.getChecksumSha256(),
                run.getStatus(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getTotalLines(),
                run.getMatchedExact(),
                run.getMatchedHeuristic(),
                run.getUnmatched(),
                roundToBasisPoints(run.matchRate()),
                run.getDiscrepancyCount(),
                run.getMatchedAmountMinor(),
                Money.of(run.getMatchedAmountMinor(), currency).toMajorUnits(),
                run.getUnmatchedAmountMinor(),
                Money.of(run.getUnmatchedAmountMinor(), currency).toMajorUnits(),
                outstandingMinor,
                Money.of(outstandingMinor, currency).toMajorUnits(),
                currency,
                run.getError(),
                breakdown);
    }

    /** The lines a run could not account for, which is what a drill-down from the report opens. */
    @Transactional(readOnly = true)
    public Page<SettlementLine> unmatchedLines(UUID runId, Pageable pageable) {
        runs.get(runId);
        return matchResults.findLinesByStatus(runId, Set.of(MatchStatus.UNMATCHED), pageable);
    }

    /** Two decimal places of a percentage; enough precision for a headline figure. */
    private double roundToBasisPoints(double rate) {
        return Math.round(rate * 10_000d) / 10_000d;
    }
}
