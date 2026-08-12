package com.reconengine.ledger;

import com.reconengine.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One immutable fact from our own books. Never updated or deleted: an append-only trigger
 * enforces that in the database, and {@link Immutable} stops Hibernate from even trying.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private LedgerEntryType entryType;

    /** Our own identifier for the business event, e.g. the order number. */
    @Column(name = "external_ref", nullable = false, updatable = false)
    private String externalRef;

    /** The provider's identifier if we know it at the time of posting, e.g. a Stripe charge id. */
    @Column(name = "provider_ref", updatable = false)
    private String providerRef;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> metadata = Map.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(LedgerEntryType entryType, String externalRef, String providerRef, Money amount,
                       Instant occurredAt, String description, Map<String, String> metadata) {
        this.entryType = entryType;
        this.externalRef = externalRef;
        this.providerRef = providerRef;
        this.amountMinor = amount.minorUnits();
        this.currency = amount.currency();
        this.occurredAt = occurredAt;
        this.description = description;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public UUID getId() {
        return id;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Money getAmount() {
        return Money.of(amountMinor, currency);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
