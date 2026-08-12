package com.reconengine.settlement;

import com.reconengine.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One provider row exactly as delivered, kept verbatim in {@code raw} so any figure in a
 * report can be traced back to the bytes it came from.
 */
@Entity
@Immutable
@Table(name = "settlement_lines")
public class SettlementLine {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;

    /** The provider's balance-transaction id, e.g. {@code txn_...}. */
    @Column(name = "provider_txn_id", nullable = false, updatable = false)
    private String providerTxnId;

    /** The originating object, e.g. {@code ch_...} or {@code re_...}. This is the exact-match key. */
    @Column(name = "provider_ref", updatable = false)
    private String providerRef;

    @Column(name = "txn_type", nullable = false, updatable = false)
    private String txnType;

    @Column(name = "gross_minor", nullable = false, updatable = false)
    private long grossMinor;

    @Column(name = "fee_minor", nullable = false, updatable = false)
    private long feeMinor;

    @Column(name = "net_minor", nullable = false, updatable = false)
    private long netMinor;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "created_at_provider", nullable = false, updatable = false)
    private Instant createdAtProvider;

    @Column(name = "available_on", updatable = false)
    private Instant availableOn;

    @Column(updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private Map<String, String> raw = Map.of();

    protected SettlementLine() {
    }

    public SettlementLine(UUID fileId, ParsedSettlementLine parsed) {
        this.fileId = fileId;
        this.lineNumber = parsed.lineNumber();
        this.providerTxnId = parsed.providerTxnId();
        this.providerRef = parsed.providerRef();
        this.txnType = parsed.txnType();
        this.grossMinor = parsed.grossMinor();
        this.feeMinor = parsed.feeMinor();
        this.netMinor = parsed.netMinor();
        this.currency = parsed.currency();
        this.createdAtProvider = parsed.createdAt();
        this.availableOn = parsed.availableOn();
        this.description = parsed.description();
        this.raw = parsed.raw() == null ? Map.of() : Map.copyOf(parsed.raw());
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getProviderTxnId() {
        return providerTxnId;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getTxnType() {
        return txnType;
    }

    public long getGrossMinor() {
        return grossMinor;
    }

    public long getFeeMinor() {
        return feeMinor;
    }

    public long getNetMinor() {
        return netMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public Money getGross() {
        return Money.of(grossMinor, currency);
    }

    public Money getFee() {
        return Money.of(feeMinor, currency);
    }

    public Money getNet() {
        return Money.of(netMinor, currency);
    }

    public Instant getCreatedAtProvider() {
        return createdAtProvider;
    }

    public Instant getAvailableOn() {
        return availableOn;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getRaw() {
        return raw;
    }
}
