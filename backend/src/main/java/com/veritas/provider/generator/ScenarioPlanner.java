package com.veritas.provider.generator;

import com.veritas.common.Errors;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a reproducible scenario. The same seed always yields the same transactions and the
 * same injected defects, so the reconciliation figures a demo produces are stable and can be
 * asserted in tests.
 */
@Component
public class ScenarioPlanner {

    /** Stripe's standard US card pricing, used so generated fees look like real ones. */
    private static final long FEE_FIXED_MINOR = 30L;
    private static final double FEE_PERCENT = 0.029;

    private final Clock clock;

    public ScenarioPlanner(Clock clock) {
        this.clock = clock;
    }

    public ScenarioPlan plan(ScenarioRequest request) {
        int reserved = request.reservedTransactions();
        if (reserved > request.transactions()) {
            throw new Errors.Unprocessable("TOO_MANY_RESERVED_TRANSACTIONS",
                    "Requested %d defective or heuristic-only transactions out of only %d."
                            .formatted(reserved, request.transactions()));
        }

        Random random = new Random(request.seed());
        Instant windowEnd = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        List<ScenarioPlan.Defect> defectSlots = defectSlots(request);
        List<ScenarioPlan.PlannedTransaction> transactions = new ArrayList<>(request.transactions());

        for (int i = 0; i < request.transactions(); i++) {
            ScenarioPlan.Defect defect = i < defectSlots.size() ? defectSlots.get(i) : null;
            boolean omitProviderRef = defect == null
                    && i >= defectSlots.size()
                    && i < defectSlots.size() + request.heuristicOnly();

            long gross = 500L + random.nextInt(24_500);
            long fee = Math.round(gross * FEE_PERCENT) + FEE_FIXED_MINOR;
            Instant occurredAt = windowEnd.minus(Duration.ofMinutes(random.nextInt(30 * 24 * 60)));

            boolean refunded = defect == null && !omitProviderRef && random.nextInt(100) < 12;
            long refundMinor = refunded ? -gross : 0L;
            Instant refundedAt = refunded ? occurredAt.plus(Duration.ofHours(1 + random.nextInt(72))) : null;

            long delta = switch (defect) {
                case null -> 0L;
                case AMOUNT_DRIFT -> (random.nextBoolean() ? 1 : -1) * (50L + random.nextInt(400));
                case FX_ROUNDING -> random.nextBoolean() ? 1L : -1L;
                default -> 0L;
            };

            String suffix = "%s%04d".formatted(Long.toString(request.seed(), 36), i);
            transactions.add(new ScenarioPlan.PlannedTransaction(
                    "ORD-" + suffix.toUpperCase(),
                    "ch_" + suffix,
                    "txn_" + suffix,
                    gross,
                    fee,
                    occurredAt,
                    refunded,
                    refunded ? "re_" + suffix : null,
                    refunded ? "txnr_" + suffix : null,
                    refundMinor,
                    refundedAt,
                    defect,
                    delta,
                    omitProviderRef));
        }

        return new ScenarioPlan(request.seed(), request.currency(), List.copyOf(transactions));
    }

    /**
     * Defects occupy the first N transactions rather than being scattered randomly, which keeps
     * the plan readable when someone inspects a generated file by hand.
     */
    private List<ScenarioPlan.Defect> defectSlots(ScenarioRequest request) {
        List<ScenarioPlan.Defect> slots = new ArrayList<>();
        addAll(slots, ScenarioPlan.Defect.MISSING_PAYOUT, request.missingPayouts());
        addAll(slots, ScenarioPlan.Defect.MISSING_LEDGER_ENTRY, request.missingLedgerEntries());
        addAll(slots, ScenarioPlan.Defect.AMOUNT_DRIFT, request.amountDrifts());
        addAll(slots, ScenarioPlan.Defect.FX_ROUNDING, request.fxRoundings());
        addAll(slots, ScenarioPlan.Defect.DUPLICATE_CHARGE, request.duplicateCharges());
        addAll(slots, ScenarioPlan.Defect.UNEXPECTED_FEE, request.unexpectedFees());
        return slots;
    }

    private void addAll(List<ScenarioPlan.Defect> slots, ScenarioPlan.Defect defect, int count) {
        for (int i = 0; i < count; i++) {
            slots.add(defect);
        }
    }
}
