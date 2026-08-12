package com.reconengine.settlement;

import java.time.Instant;
import java.util.Map;

/** Provider-neutral shape produced by every parser, before it becomes a persisted line. */
public record ParsedSettlementLine(
        int lineNumber,
        String providerTxnId,
        String providerRef,
        String txnType,
        long grossMinor,
        long feeMinor,
        long netMinor,
        String currency,
        Instant createdAt,
        Instant availableOn,
        String description,
        Map<String, String> raw) {
}
