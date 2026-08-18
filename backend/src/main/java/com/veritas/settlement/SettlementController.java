package com.veritas.settlement;

import com.veritas.common.Errors;
import com.veritas.common.PageResponse;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settlement-files")
@Tag(name = "Settlement files")
public class SettlementController {

    private final SettlementIngestService ingest;

    public SettlementController(SettlementIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Role.Permissions.FILE_UPLOAD + "')")
    @Operation(summary = "Upload a provider payout file; re-uploading identical bytes is a no-op")
    public ResponseEntity<SettlementDtos.FileResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "STRIPE") SettlementProvider provider) {

        if (file.isEmpty()) {
            throw new Errors.BadRequest("EMPTY_FILE", "The uploaded file is empty.");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        SettlementIngestService.Ingested result = ingest.ingest(file.getOriginalFilename(), provider, content);
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(SettlementDtos.FileResponse.from(result.file(), result.created()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.FILE_READ + "')")
    @Operation(summary = "List ingested settlement files, newest first")
    public PageResponse<SettlementDtos.FileResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(ingest.list(PageRequest.of(page, size)), SettlementDtos.FileResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Role.Permissions.FILE_READ + "')")
    @Operation(summary = "Fetch one settlement file")
    public SettlementDtos.FileResponse get(@PathVariable UUID id) {
        return SettlementDtos.FileResponse.from(ingest.get(id));
    }

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('" + Role.Permissions.FILE_READ + "')")
    @Operation(summary = "Drill down to the raw provider rows of a file")
    public PageResponse<SettlementDtos.LineResponse> lines(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return PageResponse.from(ingest.linesOf(id, PageRequest.of(page, size)), SettlementDtos.LineResponse::from);
    }
}
