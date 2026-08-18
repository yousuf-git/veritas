package com.veritas.exceptionqueue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * A human decision on one exception. Append-only: a correction is a new resolution, never an
 * edit of an old one, so the trail of who decided what and why cannot be rewritten.
 */
@Entity
@Immutable
@Table(name = "discrepancy_resolutions")
public class DiscrepancyResolution {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "discrepancy_id", nullable = false, updatable = false)
    private UUID discrepancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ResolutionAction action;

    @Column(name = "linked_ledger_entry_id", updatable = false)
    private UUID linkedLedgerEntryId;

    @Column(nullable = false, updatable = false)
    private String note;

    @Column(name = "resolved_by", nullable = false, updatable = false)
    private UUID resolvedBy;

    @Column(name = "resolved_by_username", nullable = false, updatable = false)
    private String resolvedByUsername;

    @CreationTimestamp
    @Column(name = "resolved_at", nullable = false, updatable = false)
    private Instant resolvedAt;

    protected DiscrepancyResolution() {
    }

    public DiscrepancyResolution(UUID discrepancyId, ResolutionAction action, UUID linkedLedgerEntryId,
                                 String note, UUID resolvedBy, String resolvedByUsername) {
        this.discrepancyId = discrepancyId;
        this.action = action;
        this.linkedLedgerEntryId = linkedLedgerEntryId;
        this.note = note;
        this.resolvedBy = resolvedBy;
        this.resolvedByUsername = resolvedByUsername;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDiscrepancyId() {
        return discrepancyId;
    }

    public ResolutionAction getAction() {
        return action;
    }

    public UUID getLinkedLedgerEntryId() {
        return linkedLedgerEntryId;
    }

    public String getNote() {
        return note;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public String getResolvedByUsername() {
        return resolvedByUsername;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
