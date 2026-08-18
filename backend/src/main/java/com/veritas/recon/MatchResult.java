package com.veritas.recon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The verdict for one settlement line in one run. {@code reason} is written for a human in the
 * exception queue: every line records why it matched or why it did not.
 */
@Entity
@Table(name = "match_results")
public class MatchResult {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "settlement_line_id", nullable = false, updatable = false)
    private UUID settlementLineId;

    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_stage", nullable = false)
    private MatchStage matchStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private MatchStatus matchStatus;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence = BigDecimal.ZERO;

    /** Provider amount minus ledger amount, in minor units. Zero for an exact agreement. */
    @Column(name = "amount_delta_minor", nullable = false)
    private long amountDeltaMinor;

    @Column(nullable = false)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MatchResult() {
    }

    private MatchResult(UUID runId, UUID settlementLineId, UUID ledgerEntryId, MatchStage matchStage,
                        MatchStatus matchStatus, BigDecimal confidence, long amountDeltaMinor, String reason) {
        this.runId = runId;
        this.settlementLineId = settlementLineId;
        this.ledgerEntryId = ledgerEntryId;
        this.matchStage = matchStage;
        this.matchStatus = matchStatus;
        this.confidence = confidence;
        this.amountDeltaMinor = amountDeltaMinor;
        this.reason = reason;
    }

    public static MatchResult matched(UUID runId, UUID settlementLineId, UUID ledgerEntryId, MatchStage stage,
                                      double confidence, long amountDeltaMinor, String reason) {
        MatchStatus status = amountDeltaMinor == 0 ? MatchStatus.MATCHED : MatchStatus.PARTIAL;
        return new MatchResult(runId, settlementLineId, ledgerEntryId, stage, status,
                BigDecimal.valueOf(confidence).setScale(4, java.math.RoundingMode.HALF_UP),
                amountDeltaMinor, reason);
    }

    public static MatchResult unmatched(UUID runId, UUID settlementLineId, String reason) {
        return new MatchResult(runId, settlementLineId, null, MatchStage.NONE, MatchStatus.UNMATCHED,
                BigDecimal.ZERO, 0L, reason);
    }

    /**
     * Applied when an analyst links an unmatched line to a ledger entry by hand. The same
     * partial unique index that governs automatic matching still applies, so a manual link
     * cannot quietly attach a ledger entry that another line already claimed.
     */
    public void linkManually(UUID ledgerEntryId, long amountDeltaMinor, String reason) {
        this.ledgerEntryId = ledgerEntryId;
        this.matchStage = MatchStage.MANUAL;
        this.matchStatus = amountDeltaMinor == 0 ? MatchStatus.MATCHED : MatchStatus.PARTIAL;
        this.amountDeltaMinor = amountDeltaMinor;
        this.confidence = BigDecimal.ONE.setScale(4, java.math.RoundingMode.HALF_UP);
        this.reason = reason;
    }

    public static MatchResult excluded(UUID runId, UUID settlementLineId, String reason) {
        return new MatchResult(runId, settlementLineId, null, MatchStage.NONE, MatchStatus.EXCLUDED,
                BigDecimal.ZERO, 0L, reason);
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getSettlementLineId() {
        return settlementLineId;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public MatchStage getMatchStage() {
        return matchStage;
    }

    public MatchStatus getMatchStatus() {
        return matchStatus;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public long getAmountDeltaMinor() {
        return amountDeltaMinor;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
