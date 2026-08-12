package com.reconengine.recon.matching;

import com.reconengine.common.Money;
import com.reconengine.recon.DiscrepancyType;
import com.reconengine.recon.MatchResult;
import com.reconengine.recon.Severity;

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
