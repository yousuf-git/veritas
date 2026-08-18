package com.veritas.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class SettlementDtos {

    private SettlementDtos() {
    }

    public record FileResponse(
            String id,
            String filename,
            SettlementProvider provider,
            String checksumSha256,
            long sizeBytes,
            SettlementFileStatus status,
            int lineCount,
            String parseError,
            Instant uploadedAt,
            /* False when the upload was recognised as a byte-identical re-upload and ignored. */
            Boolean newlyIngested) {

        public static FileResponse from(SettlementFile file, Boolean newlyIngested) {
            return new FileResponse(
                    file.getId().toString(),
                    file.getFilename(),
                    file.getProvider(),
                    file.getChecksumSha256(),
                    file.getSizeBytes(),
                    file.getStatus(),
                    file.getLineCount(),
                    file.getParseError(),
                    file.getUploadedAt(),
                    newlyIngested);
        }

        public static FileResponse from(SettlementFile file) {
            return from(file, null);
        }
    }

    public record LineResponse(
            String id,
            int lineNumber,
            String providerTxnId,
            String providerRef,
            String txnType,
            long grossMinor,
            long feeMinor,
            long netMinor,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal net,
            String currency,
            Instant createdAtProvider,
            Instant availableOn,
            String description,
            Map<String, String> raw) {

        public static LineResponse from(SettlementLine line) {
            return new LineResponse(
                    line.getId().toString(),
                    line.getLineNumber(),
                    line.getProviderTxnId(),
                    line.getProviderRef(),
                    line.getTxnType(),
                    line.getGrossMinor(),
                    line.getFeeMinor(),
                    line.getNetMinor(),
                    line.getGross().toMajorUnits(),
                    line.getFee().toMajorUnits(),
                    line.getNet().toMajorUnits(),
                    line.getCurrency(),
                    line.getCreatedAtProvider(),
                    line.getAvailableOn(),
                    line.getDescription(),
                    line.getRaw());
        }
    }
}
