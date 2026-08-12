package com.reconengine.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record CreateEntryRequest(
            @NotNull LedgerEntryType entryType,
            @NotBlank @Size(max = 128) String externalRef,
            @Size(max = 128) String providerRef,
            /* Minor units, signed. Deliberately not a decimal: the wire format cannot lose a cent. */
            @NotNull Long amountMinor,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO-4217 code") String currency,
            @NotNull Instant occurredAt,
            @Size(max = 512) String description,
            Map<String, String> metadata) {
    }

    public record BatchCreateRequest(
            @NotNull @Size(min = 1, max = 5000) java.util.List<@jakarta.validation.Valid CreateEntryRequest> entries) {
    }

    public record BatchCreateResponse(int created, int alreadyPresent) {
    }

    public record EntryResponse(
            String id,
            LedgerEntryType entryType,
            String externalRef,
            String providerRef,
            long amountMinor,
            BigDecimal amount,
            String currency,
            Instant occurredAt,
            String description,
            Map<String, String> metadata,
            Instant createdAt) {

        public static EntryResponse from(LedgerEntry entry) {
            return new EntryResponse(
                    entry.getId().toString(),
                    entry.getEntryType(),
                    entry.getExternalRef(),
                    entry.getProviderRef(),
                    entry.getAmountMinor(),
                    entry.getAmount().toMajorUnits(),
                    entry.getCurrency(),
                    entry.getOccurredAt(),
                    entry.getDescription(),
                    entry.getMetadata(),
                    entry.getCreatedAt());
        }
    }
}
