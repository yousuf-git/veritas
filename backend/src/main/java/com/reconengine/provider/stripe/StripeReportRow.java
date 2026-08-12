package com.reconengine.provider.stripe;

import java.time.Instant;

/**
 * One row of a Stripe itemized balance report, in minor units. Rendering to the report's
 * major-unit text format happens in {@link StripeCsvRenderer}.
 */
public record StripeReportRow(
        String balanceTransactionId,
        Instant createdAt,
        Instant availableOn,
        String currency,
        long grossMinor,
        long feeMinor,
        String reportingCategory,
        String sourceId,
        String chargeId,
        String description) {

    public long netMinor() {
        return grossMinor - feeMinor;
    }
}
