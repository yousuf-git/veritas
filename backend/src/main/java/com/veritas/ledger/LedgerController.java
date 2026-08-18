package com.veritas.ledger;

import com.veritas.common.PageResponse;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger/entries")
@Tag(name = "Internal ledger")
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.LEDGER_WRITE + "')")
    @Operation(summary = "Post an immutable ledger entry; re-posting the same reference is a no-op")
    public ResponseEntity<LedgerDtos.EntryResponse> post(@Valid @RequestBody LedgerDtos.CreateEntryRequest request) {
        LedgerService.Posted posted = ledger.post(request);
        return ResponseEntity
                .status(posted.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(LedgerDtos.EntryResponse.from(posted.entry()));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('" + Role.Permissions.LEDGER_WRITE + "')")
    @Operation(summary = "Post many ledger entries; duplicates are counted, not rejected")
    public LedgerDtos.BatchCreateResponse postBatch(@Valid @RequestBody LedgerDtos.BatchCreateRequest request) {
        int created = 0;
        int alreadyPresent = 0;
        for (LedgerDtos.CreateEntryRequest entry : request.entries()) {
            if (ledger.post(entry).created()) {
                created++;
            } else {
                alreadyPresent++;
            }
        }
        return new LedgerDtos.BatchCreateResponse(created, alreadyPresent);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.LEDGER_READ + "')")
    @Operation(summary = "Search the internal ledger")
    public PageResponse<LedgerDtos.EntryResponse> search(
            @RequestParam(required = false) LedgerEntryType entryType,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String externalRef,
            @RequestParam(required = false) String providerRef,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.from(
                ledger.search(entryType, currency, externalRef, providerRef, from, to, pageable),
                LedgerDtos.EntryResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Role.Permissions.LEDGER_READ + "')")
    @Operation(summary = "Fetch one ledger entry")
    public LedgerDtos.EntryResponse get(@PathVariable UUID id) {
        return LedgerDtos.EntryResponse.from(ledger.get(id));
    }
}
