package com.veritas.exceptionqueue;

import com.veritas.ledger.LedgerDtos;
import com.veritas.recon.Discrepancy;
import com.veritas.recon.DiscrepancyStatus;
import com.veritas.recon.DiscrepancyType;
import com.veritas.recon.Severity;
import com.veritas.settlement.SettlementDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ExceptionDtos {

    private ExceptionDtos() {
    }

    public record ExceptionSummary(
            String id,
            String runId,
            DiscrepancyType type,
            Severity severity,
            DiscrepancyStatus status,
            long amountImpactMinor,
            BigDecimal amountImpact,
            String currency,
            String detail,
            String assignedTo,
            int version,
            Instant createdAt,
            Instant resolvedAt) {

        public static ExceptionSummary from(Discrepancy discrepancy) {
            return new ExceptionSummary(
                    discrepancy.getId().toString(),
                    discrepancy.getRunId().toString(),
                    discrepancy.getType(),
                    discrepancy.getSeverity(),
                    discrepancy.getStatus(),
                    discrepancy.getAmountImpactMinor(),
                    discrepancy.getAmountImpact().toMajorUnits(),
                    discrepancy.getCurrency(),
                    discrepancy.getDetail(),
                    discrepancy.getAssignedTo() == null ? null : discrepancy.getAssignedTo().toString(),
                    discrepancy.getVersion(),
                    discrepancy.getCreatedAt(),
                    discrepancy.getResolvedAt());
        }
    }

    /** The full picture an analyst needs: the exception plus both sides of the money it concerns. */
    public record ExceptionDetail(
            ExceptionSummary exception,
            SettlementDtos.LineResponse settlementLine,
            LedgerDtos.EntryResponse ledgerEntry,
            List<ResolutionResponse> history) {
    }

    public record ResolutionResponse(
            String id,
            ResolutionAction action,
            String linkedLedgerEntryId,
            String note,
            String resolvedByUsername,
            Instant resolvedAt) {

        public static ResolutionResponse from(DiscrepancyResolution resolution) {
            return new ResolutionResponse(
                    resolution.getId().toString(),
                    resolution.getAction(),
                    resolution.getLinkedLedgerEntryId() == null
                            ? null : resolution.getLinkedLedgerEntryId().toString(),
                    resolution.getNote(),
                    resolution.getResolvedByUsername(),
                    resolution.getResolvedAt());
        }
    }

    public record ResolveRequest(
            @NotNull ResolutionAction action,
            @NotBlank @Size(max = 1024, message = "a resolution must say why") String note,
            UUID linkedLedgerEntryId,
            /* The version the analyst was looking at; rejects a decision made on a stale screen. */
            Integer version) {
    }

    public record ClaimRequest(Integer version) {
    }
}
