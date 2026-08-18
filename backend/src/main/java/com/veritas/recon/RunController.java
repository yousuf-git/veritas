package com.veritas.recon;

import com.veritas.common.PageResponse;
import com.veritas.report.ReportDtos;
import com.veritas.report.ReportService;
import com.veritas.settlement.SettlementDtos;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
@Tag(name = "Reconciliation runs")
public class RunController {

    private final ReconciliationService runs;
    private final ReportService reports;

    public RunController(ReconciliationService runs, ReportService reports) {
        this.runs = runs;
        this.reports = reports;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_TRIGGER + "')")
    @Operation(summary = "Start reconciling a parsed file; returns immediately while matching runs")
    public ResponseEntity<RunResponse> trigger(@RequestParam UUID fileId) {
        ReconciliationRun run = runs.trigger(fileId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(RunResponse.from(run));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_READ + "')")
    @Operation(summary = "List reconciliation runs, newest first")
    public PageResponse<RunResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(runs.list(PageRequest.of(page, size)), RunResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_READ + "')")
    @Operation(summary = "Poll a run's status and headline counters")
    public RunResponse get(@PathVariable UUID id) {
        return RunResponse.from(runs.get(id));
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_READ + "')")
    @Operation(summary = "Full reconciliation report for a run")
    public ReportDtos.RunReport report(@PathVariable UUID id) {
        return reports.report(id);
    }

    @GetMapping("/{id}/unmatched-lines")
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_READ + "')")
    @Operation(summary = "Drill down to the provider rows a run could not account for")
    public PageResponse<SettlementDtos.LineResponse> unmatchedLines(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.from(reports.unmatchedLines(id, PageRequest.of(page, size)),
                SettlementDtos.LineResponse::from);
    }

    @GetMapping("/by-file/{fileId}")
    @PreAuthorize("hasAuthority('" + Role.Permissions.RUN_READ + "')")
    @Operation(summary = "Every run performed against one file")
    public List<RunResponse> forFile(@PathVariable UUID fileId) {
        return runs.forFile(fileId).stream().map(RunResponse::from).toList();
    }

    public record RunResponse(
            String id,
            String fileId,
            RunStatus status,
            int totalLines,
            int matchedExact,
            int matchedHeuristic,
            int unmatched,
            int discrepancyCount,
            double matchRate,
            long matchedAmountMinor,
            long unmatchedAmountMinor,
            String currency,
            String error,
            Instant startedAt,
            Instant completedAt) {

        public static RunResponse from(ReconciliationRun run) {
            return new RunResponse(
                    run.getId().toString(),
                    run.getFileId().toString(),
                    run.getStatus(),
                    run.getTotalLines(),
                    run.getMatchedExact(),
                    run.getMatchedHeuristic(),
                    run.getUnmatched(),
                    run.getDiscrepancyCount(),
                    run.matchRate(),
                    run.getMatchedAmountMinor(),
                    run.getUnmatchedAmountMinor(),
                    run.getCurrency(),
                    run.getError(),
                    run.getStartedAt(),
                    run.getCompletedAt());
        }
    }
}
