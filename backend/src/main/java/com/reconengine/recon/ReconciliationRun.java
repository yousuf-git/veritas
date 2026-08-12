package com.reconengine.recon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "triggered_by", updatable = false)
    private UUID triggeredBy;

    @Column(name = "batch_job_execution_id")
    private Long batchJobExecutionId;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "matched_exact", nullable = false)
    private int matchedExact;

    @Column(name = "matched_heuristic", nullable = false)
    private int matchedHeuristic;

    @Column(nullable = false)
    private int unmatched;

    @Column(name = "discrepancy_count", nullable = false)
    private int discrepancyCount;

    @Column(name = "matched_amount_minor", nullable = false)
    private long matchedAmountMinor;

    @Column(name = "unmatched_amount_minor", nullable = false)
    private long unmatchedAmountMinor;

    @Column(length = 3)
    private String currency;

    @Column
    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReconciliationRun() {
    }

    public ReconciliationRun(UUID fileId, UUID triggeredBy, Instant startedAt) {
        this.fileId = fileId;
        this.triggeredBy = triggeredBy;
        this.startedAt = startedAt;
        this.status = RunStatus.PENDING;
    }

    public void markRunning(Long batchJobExecutionId) {
        this.status = RunStatus.RUNNING;
        this.batchJobExecutionId = batchJobExecutionId;
    }

    public void markCompleted(Summary summary, Instant completedAt) {
        this.status = RunStatus.COMPLETED;
        this.totalLines = summary.totalLines();
        this.matchedExact = summary.matchedExact();
        this.matchedHeuristic = summary.matchedHeuristic();
        this.unmatched = summary.unmatched();
        this.discrepancyCount = summary.discrepancyCount();
        this.matchedAmountMinor = summary.matchedAmountMinor();
        this.unmatchedAmountMinor = summary.unmatchedAmountMinor();
        this.currency = summary.currency();
        this.completedAt = completedAt;
        this.error = null;
    }

    public void markFailed(String error, Instant completedAt) {
        this.status = RunStatus.FAILED;
        this.error = error == null ? "unknown failure" : truncate(error);
        this.completedAt = completedAt;
    }

    private static String truncate(String value) {
        return value.length() <= 1024 ? value : value.substring(0, 1021) + "...";
    }

    /**
     * Share of reconcilable lines tied to a ledger entry — the headline number of a run.
     * Excluded rows such as payouts are not counted either way, since they were never
     * expected to appear in the ledger.
     */
    public double matchRate() {
        int matched = matchedExact + matchedHeuristic;
        int reconcilable = matched + unmatched;
        return reconcilable == 0 ? 0d : (double) matched / reconcilable;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public UUID getTriggeredBy() {
        return triggeredBy;
    }

    public Long getBatchJobExecutionId() {
        return batchJobExecutionId;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getMatchedExact() {
        return matchedExact;
    }

    public int getMatchedHeuristic() {
        return matchedHeuristic;
    }

    public int getUnmatched() {
        return unmatched;
    }

    public int getDiscrepancyCount() {
        return discrepancyCount;
    }

    public long getMatchedAmountMinor() {
        return matchedAmountMinor;
    }

    public long getUnmatchedAmountMinor() {
        return unmatchedAmountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public String getError() {
        return error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public record Summary(int totalLines, int matchedExact, int matchedHeuristic, int unmatched,
                          int discrepancyCount, long matchedAmountMinor, long unmatchedAmountMinor,
                          String currency) {
    }
}
