package com.reconengine.ledger;

import com.reconengine.common.Errors;
import com.reconengine.common.Money;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    /**
     * Posting the same (type, externalRef) twice returns the original entry instead of
     * creating a second one, so a retried or replayed ingestion cannot double-book.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Posted post(LedgerDtos.CreateEntryRequest request) {
        Money amount = Money.of(request.amountMinor(), request.currency());
        requireSignMatchesType(request.entryType(), amount);

        LedgerEntry entry = new LedgerEntry(
                request.entryType(),
                request.externalRef(),
                request.providerRef(),
                amount,
                request.occurredAt(),
                request.description(),
                request.metadata());

        try {
            return new Posted(entries.saveAndFlush(entry), true);
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint is the arbiter, not a prior existence check: no TOCTOU window.
            return entries.findByEntryTypeAndExternalRef(request.entryType(), request.externalRef())
                    .map(existing -> new Posted(existing, false))
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(readOnly = true)
    public LedgerEntry get(UUID id) {
        return entries.findById(id).orElseThrow(() -> new Errors.NotFound("Ledger entry", id));
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntry> search(LedgerEntryType entryType, String currency, String externalRef,
                                    String providerRef, Instant from, Instant to, Pageable pageable) {
        return entries.search(entryType, currency, externalRef, providerRef, from, to, pageable);
    }

    /**
     * Rejected here with a readable message rather than left to the database CHECK, which would
     * surface as an opaque constraint violation.
     */
    private void requireSignMatchesType(LedgerEntryType type, Money amount) {
        if (amount.isZero()) {
            throw new Errors.Unprocessable("ZERO_AMOUNT", "A ledger entry cannot have a zero amount.");
        }
        boolean valid = switch (type) {
            case ORDER -> amount.signum() > 0;
            case REFUND, FEE -> amount.signum() < 0;
            case ADJUSTMENT -> true;
        };
        if (!valid) {
            throw new Errors.Unprocessable("SIGN_MISMATCH",
                    "A %s must be %s, got %s".formatted(type,
                            type == LedgerEntryType.ORDER ? "positive" : "negative", amount));
        }
    }

    public record Posted(LedgerEntry entry, boolean created) {
    }
}
