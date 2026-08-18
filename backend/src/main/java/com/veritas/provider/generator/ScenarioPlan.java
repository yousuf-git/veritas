package com.veritas.provider.generator;

import java.time.Instant;
import java.util.List;

/**
 * The intended shape of a demo scenario: every synthetic transaction, and the defect (if any)
 * deliberately introduced into it. Both the internal ledger and the provider file are derived
 * from this one plan, which is what makes the expected discrepancy counts knowable up front.
 */
public record ScenarioPlan(long seed, String currency, List<PlannedTransaction> transactions) {

    public enum Defect {
        /** Ledger has the order; the provider file never reports it. */
        MISSING_PAYOUT,
        /** The provider file reports money we have no ledger entry for. */
        MISSING_LEDGER_ENTRY,
        /** Provider gross differs from the ledger amount by more than the tolerance. */
        AMOUNT_DRIFT,
        /** Provider gross differs by a minor unit or two, inside the tolerance. */
        FX_ROUNDING,
        /** The same source object is settled twice in one file. */
        DUPLICATE_CHARGE,
        /** The provider deducted a fee we never booked. */
        UNEXPECTED_FEE
    }

    public record PlannedTransaction(
            String orderRef,
            String chargeId,
            String balanceTxnId,
            long grossMinor,
            long feeMinor,
            Instant occurredAt,
            boolean refunded,
            String refundId,
            String refundBalanceTxnId,
            long refundMinor,
            Instant refundedAt,
            Defect defect,
            /* Signed adjustment applied to the provider-side gross, used by the drift defects. */
            long providerGrossDelta,
            /*
             * Emits the provider row without a source id, which is common for bank-style
             * settlements and forces the pipeline past exact matching into the heuristic stage.
             */
            boolean omitProviderRef) {

        public long providerGrossMinor() {
            return grossMinor + providerGrossDelta;
        }

        public boolean hasDefect(Defect candidate) {
            return defect == candidate;
        }
    }
}
