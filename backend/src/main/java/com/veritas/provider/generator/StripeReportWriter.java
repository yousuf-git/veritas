package com.veritas.provider.generator;

import com.veritas.provider.stripe.StripeCsvRenderer;
import com.veritas.provider.stripe.StripeReportRow;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Renders a scenario plan as a Stripe itemized balance report, honouring each planned defect:
 * a missing payout simply has no row, a duplicate charge has two, and a drifted amount is
 * written as the provider would have reported it rather than as the ledger holds it.
 */
@Component
public class StripeReportWriter {

    /** Stripe funds are typically available two days after the transaction. */
    private static final Duration AVAILABILITY_DELAY = Duration.ofDays(2);

    private final StripeCsvRenderer renderer;

    public StripeReportWriter(StripeCsvRenderer renderer) {
        this.renderer = renderer;
    }

    public byte[] write(ScenarioPlan plan) {
        List<StripeReportRow> rows = new ArrayList<>();

        for (ScenarioPlan.PlannedTransaction txn : plan.transactions()) {
            if (txn.hasDefect(ScenarioPlan.Defect.MISSING_PAYOUT)) {
                continue;
            }

            rows.add(chargeRow(txn, plan.currency(), txn.balanceTxnId()));

            if (txn.hasDefect(ScenarioPlan.Defect.DUPLICATE_CHARGE)) {
                // The same source object settled a second time under a different balance transaction.
                rows.add(chargeRow(txn, plan.currency(), txn.balanceTxnId() + "_dup"));
            }

            if (txn.refunded()) {
                rows.add(refundRow(txn, plan.currency()));
            }
        }

        rows.sort(Comparator.comparing(StripeReportRow::createdAt)
                .thenComparing(StripeReportRow::balanceTransactionId));
        return renderer.render(rows);
    }

    private StripeReportRow chargeRow(ScenarioPlan.PlannedTransaction txn, String currency, String balanceTxnId) {
        // With no source id the description is the only handle left, which is exactly the
        // situation the heuristic matching stage exists for.
        String sourceId = txn.omitProviderRef() ? null : txn.chargeId();

        return new StripeReportRow(
                balanceTxnId,
                txn.occurredAt(),
                txn.occurredAt().plus(AVAILABILITY_DELAY),
                currency,
                txn.providerGrossMinor(),
                txn.feeMinor(),
                "charge",
                sourceId,
                sourceId,
                "Payment for " + txn.orderRef());
    }

    private StripeReportRow refundRow(ScenarioPlan.PlannedTransaction txn, String currency) {
        // Stripe does not charge a processing fee on a refund of the original amount.
        return new StripeReportRow(
                txn.refundBalanceTxnId(),
                txn.refundedAt(),
                txn.refundedAt().plus(AVAILABILITY_DELAY),
                currency,
                txn.refundMinor(),
                0L,
                "refund",
                txn.refundId(),
                txn.chargeId(),
                "Refund for " + txn.orderRef());
    }
}
