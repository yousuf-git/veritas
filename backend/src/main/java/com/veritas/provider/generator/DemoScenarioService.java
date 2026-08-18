package com.veritas.provider.generator;

import com.veritas.common.Money;
import com.veritas.ledger.LedgerEntry;
import com.veritas.ledger.LedgerEntryRepository;
import com.veritas.ledger.LedgerEntryType;
import com.veritas.settlement.SettlementFile;
import com.veritas.settlement.SettlementIngestService;
import com.veritas.settlement.SettlementProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds an internal ledger and the matching provider file from one plan, so a demo can be
 * reconciled end to end without any Stripe credentials. The defects are known in advance,
 * which makes the resulting exception queue verifiable rather than merely plausible.
 */
@Service
public class DemoScenarioService {

    private static final Logger log = LoggerFactory.getLogger(DemoScenarioService.class);

    private final ScenarioPlanner planner;
    private final StripeReportWriter reportWriter;
    private final LedgerEntryRepository ledgerEntries;
    private final SettlementIngestService ingest;

    public DemoScenarioService(ScenarioPlanner planner, StripeReportWriter reportWriter,
                               LedgerEntryRepository ledgerEntries, SettlementIngestService ingest) {
        this.planner = planner;
        this.reportWriter = reportWriter;
        this.ledgerEntries = ledgerEntries;
        this.ingest = ingest;
    }

    @Transactional
    public Result generate(ScenarioRequest request) {
        ScenarioPlan plan = planner.plan(request);

        int ledgerCreated = seedLedger(plan);
        byte[] report = reportWriter.write(plan);
        String filename = "stripe-balance-report-seed-%d.csv".formatted(plan.seed());

        SettlementIngestService.Ingested ingested =
                ingest.ingest(filename, SettlementProvider.STRIPE, report);

        log.info("demo scenario seed={} created {} ledger entries and file {}",
                plan.seed(), ledgerCreated, ingested.file().getId());

        return new Result(ingested.file(), ingested.created(), ledgerCreated, expectedDiscrepancies(request));
    }

    /**
     * Only entries the plan does not deliberately omit are written. Existing external
     * references are skipped so re-running the same seed tops up rather than failing.
     */
    private int seedLedger(ScenarioPlan plan) {
        List<LedgerEntry> candidates = new ArrayList<>();

        for (ScenarioPlan.PlannedTransaction txn : plan.transactions()) {
            boolean ledgerOmitted = txn.hasDefect(ScenarioPlan.Defect.MISSING_LEDGER_ENTRY);

            if (!ledgerOmitted) {
                candidates.add(new LedgerEntry(
                        LedgerEntryType.ORDER,
                        txn.orderRef(),
                        txn.chargeId(),
                        Money.of(txn.grossMinor(), plan.currency()),
                        txn.occurredAt(),
                        "Order " + txn.orderRef(),
                        Map.of("source", "demo-scenario", "seed", String.valueOf(plan.seed()))));
            }

            // An unexpected-fee defect is exactly this: the provider deducts a fee we never booked.
            if (!ledgerOmitted && !txn.hasDefect(ScenarioPlan.Defect.UNEXPECTED_FEE)) {
                candidates.add(new LedgerEntry(
                        LedgerEntryType.FEE,
                        txn.orderRef() + "-FEE",
                        txn.chargeId(),
                        Money.of(-txn.feeMinor(), plan.currency()),
                        txn.occurredAt(),
                        "Processing fee for " + txn.orderRef(),
                        Map.of("source", "demo-scenario", "seed", String.valueOf(plan.seed()))));
            }

            if (txn.refunded()) {
                candidates.add(new LedgerEntry(
                        LedgerEntryType.REFUND,
                        txn.orderRef() + "-REFUND",
                        txn.refundId(),
                        Money.of(txn.refundMinor(), plan.currency()),
                        txn.refundedAt(),
                        "Refund for " + txn.orderRef(),
                        Map.of("source", "demo-scenario", "seed", String.valueOf(plan.seed()))));
            }
        }

        Set<String> alreadyPresent = new HashSet<>(ledgerEntries.findExistingExternalRefs(
                candidates.stream().map(LedgerEntry::getExternalRef).toList()));

        List<LedgerEntry> toInsert = candidates.stream()
                .filter(entry -> !alreadyPresent.contains(entry.getExternalRef()))
                .toList();

        ledgerEntries.saveAll(toInsert);
        return toInsert.size();
    }

    private Map<String, Integer> expectedDiscrepancies(ScenarioRequest request) {
        return Map.of(
                "MISSING_PAYOUT", request.missingPayouts(),
                "MISSING_LEDGER_ENTRY", request.missingLedgerEntries(),
                "AMOUNT_DRIFT", request.amountDrifts(),
                "FX_ROUNDING", request.fxRoundings(),
                "DUPLICATE_CHARGE", request.duplicateCharges(),
                "UNEXPECTED_FEE", request.unexpectedFees());
    }

    public record Result(SettlementFile file, boolean fileCreated, int ledgerEntriesCreated,
                         Map<String, Integer> expectedDiscrepancies) {
    }
}
