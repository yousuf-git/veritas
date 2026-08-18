package com.veritas.recon;

import com.veritas.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One item on the finance team's exception queue. Carries the money at stake so the queue can
 * be worked in order of financial impact rather than arrival.
 */
@Entity
@Table(name = "discrepancies")
public class Discrepancy {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @Column(name = "match_result_id", updatable = false)
    private UUID matchResultId;

    @Column(name = "settlement_line_id", updatable = false)
    private UUID settlementLineId;

    @Column(name = "ledger_entry_id", updatable = false)
    private UUID ledgerEntryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    @Column(name = "amount_impact_minor", nullable = false, updatable = false)
    private long amountImpactMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private String detail;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    /** Guards the queue against two analysts resolving the same item from stale screens. */
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Discrepancy() {
    }

    public Discrepancy(UUID runId, UUID matchResultId, UUID settlementLineId, UUID ledgerEntryId,
                       DiscrepancyType type, Severity severity, Money amountImpact, String detail) {
        this.runId = runId;
        this.matchResultId = matchResultId;
        this.settlementLineId = settlementLineId;
        this.ledgerEntryId = ledgerEntryId;
        this.type = type;
        this.severity = severity;
        this.amountImpactMinor = amountImpact.minorUnits();
        this.currency = amountImpact.currency();
        this.detail = detail;
    }

    public void resolve(Instant at) {
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolvedAt = at;
    }

    public void escalate() {
        this.status = DiscrepancyStatus.ESCALATED;
        this.resolvedAt = null;
    }

    public void takeForReview(UUID assignee) {
        this.status = DiscrepancyStatus.IN_REVIEW;
        this.assignedTo = assignee;
        this.resolvedAt = null;
    }

    public boolean isClosed() {
        return status == DiscrepancyStatus.RESOLVED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public UUID getMatchResultId() {
        return matchResultId;
    }

    public UUID getSettlementLineId() {
        return settlementLineId;
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public DiscrepancyType getType() {
        return type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }

    public long getAmountImpactMinor() {
        return amountImpactMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Money getAmountImpact() {
        return Money.of(amountImpactMinor, currency);
    }

    public String getDetail() {
        return detail;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
