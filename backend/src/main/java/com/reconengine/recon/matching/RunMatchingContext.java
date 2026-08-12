package com.reconengine.recon.matching;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-run state for the matching step. The claim set is what stops two settlement lines from
 * being backed by the same ledger entry: the database enforces the same rule with a partial
 * unique index, but claiming in memory lets the second line be classified as a duplicate
 * charge instead of failing the write.
 */
public class RunMatchingContext {

    private final UUID runId;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final Set<UUID> claimedLedgerEntries = new HashSet<>();

    public RunMatchingContext(UUID runId, Instant windowStart, Instant windowEnd) {
        this.runId = runId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public UUID runId() {
        return runId;
    }

    public Instant windowStart() {
        return windowStart;
    }

    public Instant windowEnd() {
        return windowEnd;
    }

    public boolean isClaimed(UUID ledgerEntryId) {
        return claimedLedgerEntries.contains(ledgerEntryId);
    }

    /** Returns false if the entry was already spoken for, which is the duplicate-charge signal. */
    public boolean claim(UUID ledgerEntryId) {
        return claimedLedgerEntries.add(ledgerEntryId);
    }

    public Set<UUID> claimedLedgerEntries() {
        return Set.copyOf(claimedLedgerEntries);
    }
}
