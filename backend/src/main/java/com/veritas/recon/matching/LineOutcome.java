package com.veritas.recon.matching;

import com.veritas.common.Money;
import com.veritas.recon.DiscrepancyType;
import com.veritas.recon.MatchResult;
import com.veritas.recon.Severity;

import java.util.List;
import java.util.UUID;

/**
 * What the matcher decided about one settlement line. Discrepancies are still pending because
 * they need the match result's identifier, which only exists once it has been written.
 */
public record LineOutcome(MatchResult result, List<PendingDiscrepancy> discrepancies) {

    public record PendingDiscrepancy(
            DiscrepancyType type,
            Severity severity,
            Money amountImpact,
            String detail,
            UUID settlementLineId,
            UUID ledgerEntryId) {
    }
}
