package com.veritas.settlement;

import com.veritas.audit.AuditService;
import com.veritas.auth.CurrentActor;
import com.veritas.common.Errors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SettlementIngestService {

    private static final Logger log = LoggerFactory.getLogger(SettlementIngestService.class);

    private final SettlementFileRepository files;
    private final SettlementLineRepository lines;
    private final FileStorage storage;
    private final List<SettlementParser> parsers;
    private final AuditService audit;
    private final CurrentActor currentActor;

    public SettlementIngestService(SettlementFileRepository files, SettlementLineRepository lines,
                                   FileStorage storage, List<SettlementParser> parsers, AuditService audit,
                                   CurrentActor currentActor) {
        this.files = files;
        this.lines = lines;
        this.storage = storage;
        this.parsers = parsers;
        this.audit = audit;
        this.currentActor = currentActor;
    }

    /**
     * Registers and parses a payout file. The content hash is the idempotency key: uploading
     * the same bytes again returns the original file record and writes nothing, so a retried
     * upload cannot produce a second set of settlement lines.
     */
    @Transactional
    public Ingested ingest(String filename, SettlementProvider provider, byte[] content) {
        if (content.length == 0) {
            throw new Errors.BadRequest("EMPTY_FILE", "The uploaded file is empty.");
        }

        String checksum = sha256(content);
        CurrentActor.Actor actor = currentActor.require();

        var existing = files.findByChecksumSha256(checksum);
        if (existing.isPresent()) {
            log.info("settlement file {} already ingested as {}", filename, existing.get().getId());
            return new Ingested(existing.get(), false);
        }

        SettlementParser parser = parserFor(provider);
        List<ParsedSettlementLine> parsed = parser.parse(new ByteArrayInputStream(content));

        FileStorage.Stored stored = storage.store(filename, new ByteArrayInputStream(content));
        SettlementFile file = new SettlementFile(filename, provider, checksum, stored.sizeBytes(),
                stored.relativePath(), actor.id());

        try {
            files.saveAndFlush(file);
        } catch (DataIntegrityViolationException ex) {
            // Two identical uploads raced; the unique checksum decided the winner.
            return files.findByChecksumSha256(checksum)
                    .map(winner -> new Ingested(winner, false))
                    .orElseThrow(() -> ex);
        }

        lines.saveAll(parsed.stream().map(line -> new SettlementLine(file.getId(), line)).toList());
        file.markParsed(parsed.size());
        files.save(file);

        audit.record(actor, "SETTLEMENT_FILE_INGESTED", "SettlementFile", file.getId(),
                Map.of("filename", filename,
                        "provider", provider.name(),
                        "lines", String.valueOf(parsed.size()),
                        "checksum", checksum));

        return new Ingested(file, true);
    }

    @Transactional(readOnly = true)
    public SettlementFile get(UUID id) {
        return files.findById(id).orElseThrow(() -> new Errors.NotFound("Settlement file", id));
    }

    @Transactional(readOnly = true)
    public Page<SettlementFile> list(Pageable pageable) {
        return files.findAllByOrderByUploadedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<SettlementLine> linesOf(UUID fileId, Pageable pageable) {
        get(fileId);
        return lines.findByFileIdOrderByLineNumber(fileId, pageable);
    }

    private SettlementParser parserFor(SettlementProvider provider) {
        return parsers.stream()
                .filter(p -> p.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new Errors.BadRequest("UNSUPPORTED_PROVIDER",
                        "No parser is configured for provider " + provider + "."));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", ex);
        }
    }

    public record Ingested(SettlementFile file, boolean created) {
    }
}
