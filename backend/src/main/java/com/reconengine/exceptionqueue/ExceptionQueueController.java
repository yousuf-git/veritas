package com.reconengine.exceptionqueue;

import com.reconengine.common.PageResponse;
import com.reconengine.ledger.LedgerDtos;
import com.reconengine.recon.Discrepancy;
import com.reconengine.recon.DiscrepancyStatus;
import com.reconengine.recon.DiscrepancyType;
import com.reconengine.recon.Severity;
import com.reconengine.settlement.SettlementDtos;
import com.reconengine.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exceptions")
@Tag(name = "Exception queue")
public class ExceptionQueueController {

    private final ExceptionQueueService queue;

    public ExceptionQueueController(ExceptionQueueService queue) {
        this.queue = queue;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.EXCEPTION_READ + "')")
    @Operation(summary = "Work the exception queue oldest first, filtered by status, type or severity")
    public PageResponse<ExceptionDtos.ExceptionSummary> search(
            @RequestParam(required = false) UUID runId,
            @RequestParam(required = false) DiscrepancyStatus status,
            @RequestParam(required = false) DiscrepancyType type,
            @RequestParam(required = false) Severity severity,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {

        // Oldest first: severity is stored as a string, so ordering by it would sort
        // alphabetically (HIGH, LOW, MEDIUM) rather than by seriousness. Callers who want the
        // worst items filter on severity instead.
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));

        return PageResponse.from(queue.search(runId, status, type, severity, pageable),
                ExceptionDtos.ExceptionSummary::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Role.Permissions.EXCEPTION_READ + "')")
    @Operation(summary = "Drill into one exception, both sides of the money and its decision history")
    public ExceptionDtos.ExceptionDetail get(@PathVariable UUID id) {
        Discrepancy discrepancy = queue.get(id);

        return new ExceptionDtos.ExceptionDetail(
                ExceptionDtos.ExceptionSummary.from(discrepancy),
                queue.settlementLineOf(discrepancy).map(SettlementDtos.LineResponse::from).orElse(null),
                queue.ledgerEntryOf(discrepancy).map(LedgerDtos.EntryResponse::from).orElse(null),
                queue.historyOf(id).stream().map(ExceptionDtos.ResolutionResponse::from).toList());
    }

    @PostMapping("/{id}/claim")
    @PreAuthorize("hasAuthority('" + Role.Permissions.EXCEPTION_RESOLVE + "')")
    @Operation(summary = "Take an exception for review")
    public ExceptionDtos.ExceptionSummary claim(@PathVariable UUID id,
                                                @RequestBody(required = false) ExceptionDtos.ClaimRequest request) {
        Integer version = request == null ? null : request.version();
        return ExceptionDtos.ExceptionSummary.from(queue.claim(id, version));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAuthority('" + Role.Permissions.EXCEPTION_RESOLVE + "')")
    @Operation(summary = "Record a decision; WRITE_OFF additionally requires approver permission")
    public ExceptionDtos.ExceptionSummary resolve(@PathVariable UUID id,
                                                  @Valid @RequestBody ExceptionDtos.ResolveRequest request) {
        return ExceptionDtos.ExceptionSummary.from(queue.resolve(
                id, request.action(), request.note(), request.linkedLedgerEntryId(), request.version()));
    }
}
