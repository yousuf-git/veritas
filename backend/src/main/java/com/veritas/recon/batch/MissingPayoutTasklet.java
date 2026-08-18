package com.veritas.recon.batch;

import com.veritas.common.Money;
import com.veritas.ledger.LedgerEntry;
import com.veritas.ledger.LedgerEntryRepository;
import com.veritas.ledger.LedgerEntryType;
import com.veritas.recon.Discrepancy;
import com.veritas.recon.DiscrepancyRepository;
import com.veritas.recon.DiscrepancyType;
import com.veritas.recon.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The other direction of the reconciliation: money we booked that the provider never settled.
 * <p>
 * Fee entries are excluded because a fee never arrives as a line of its own — it is deducted in
 * the fee column of its charge's line, and is checked there. Sweeping fees here would report
 * every ordinary fee as a missing payout.
 */
public class MissingPayoutTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(MissingPayoutTasklet.class);
    private static final Set<LedgerEntryType> SETTLED_TYPES =
            Set.of(LedgerEntryType.ORDER, LedgerEntryType.REFUND);
    private static final int PAGE_SIZE = 500;
    private static final long HIGH_SEVERITY_IMPACT_MINOR = 1_000L;

    private final LedgerEntryRepository ledgerEntries;
    private final DiscrepancyRepository discrepancies;
    private final UUID runId;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final List<String> currencies;

    public MissingPayoutTasklet(LedgerEntryRepository ledgerEntries, DiscrepancyRepository discrepancies,
                                UUID runId, Instant windowStart, Instant windowEnd, List<String> currencies) {
        this.ledgerEntries = ledgerEntries;
        this.discrepancies = discrepancies;
        this.runId = runId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.currencies = currencies;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (currencies.isEmpty()) {
            return RepeatStatus.FINISHED;
        }

        int written = 0;
        int pageNumber = 0;
        Page<LedgerEntry> page;

        do {
            // Always page zero: each pass writes discrepancies but never matches, so the
            // unsettled set does not shrink; the page number advances instead.
            page = ledgerEntries.findUnsettled(runId, SETTLED_TYPES, currencies, windowStart, windowEnd,
                    PageRequest.of(pageNumber, PAGE_SIZE));

            List<Discrepancy> batch = new ArrayList<>(page.getNumberOfElements());
            for (LedgerEntry entry : page.getContent()) {
                batch.add(toDiscrepancy(entry));
            }
            discrepancies.saveAll(batch);
            written += batch.size();
            pageNumber++;
        } while (page.hasNext());

        log.info("run {} found {} ledger entries the provider never settled", runId, written);
        contribution.incrementWriteCount(written);
        return RepeatStatus.FINISHED;
    }

    private Discrepancy toDiscrepancy(LedgerEntry entry) {
        Money impact = entry.getAmount();
        Severity severity = Math.abs(impact.minorUnits()) >= HIGH_SEVERITY_IMPACT_MINOR
                ? Severity.HIGH : Severity.MEDIUM;

        String detail = "Ledger records %s for %s on %s but the provider file settles nothing against it."
                .formatted(impact, entry.getExternalRef(), entry.getOccurredAt());

        return new Discrepancy(runId, null, null, entry.getId(),
                DiscrepancyType.MISSING_PAYOUT, severity, impact, detail);
    }
}
