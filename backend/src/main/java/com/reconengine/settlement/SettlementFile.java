package com.reconengine.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_files")
public class SettlementFile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementProvider provider;

    /** Content hash, uniquely constrained: the same bytes can only ever be ingested once. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementFileStatus status;

    @Column(name = "line_count", nullable = false)
    private int lineCount;

    @Column(name = "parse_error")
    private String parseError;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected SettlementFile() {
    }

    public SettlementFile(String filename, SettlementProvider provider, String checksumSha256, long sizeBytes,
                          String storagePath, UUID uploadedBy) {
        this.filename = filename;
        this.provider = provider;
        this.checksumSha256 = checksumSha256;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
        this.uploadedBy = uploadedBy;
        this.status = SettlementFileStatus.REGISTERED;
    }

    public void markParsed(int lineCount) {
        this.status = SettlementFileStatus.PARSED;
        this.lineCount = lineCount;
        this.parseError = null;
    }

    public void markParseFailed(String error) {
        this.status = SettlementFileStatus.PARSE_FAILED;
        this.parseError = error == null ? "unknown parse failure" : truncate(error);
    }

    private static String truncate(String value) {
        return value.length() <= 1024 ? value : value.substring(0, 1021) + "...";
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public SettlementProvider getProvider() {
        return provider;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public SettlementFileStatus getStatus() {
        return status;
    }

    public int getLineCount() {
        return lineCount;
    }

    public String getParseError() {
        return parseError;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
