package com.veritas.report;

import com.veritas.recon.DiscrepancyType;
import com.veritas.recon.RunStatus;
import com.veritas.recon.Severity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record RunReport(
            String runId,
            String fileId,
            String filename,
            String checksumSha256,
            RunStatus status,
            Instant startedAt,
            Instant completedAt,
            int totalLines,
            int matchedExact,
            int matchedHeuristic,
            int unmatched,
            /* Matched share of reconcilable lines, 0..1. */
            double matchRate,
            int discrepancyCount,
            long matchedAmountMinor,
            BigDecimal matchedAmount,
            long unmatchedAmountMinor,
            BigDecimal unmatchedAmount,
            /* Total money any open or closed discrepancy puts in question. */
            long outstandingAmountMinor,
            BigDecimal outstandingAmount,
            String currency,
            String error,
            List<DiscrepancyBreakdown> discrepancies) {
    }

    public record DiscrepancyBreakdown(
            DiscrepancyType type,
            Severity severity,
            long count,
            long amountAtRiskMinor,
            BigDecimal amountAtRisk) {
    }
}
