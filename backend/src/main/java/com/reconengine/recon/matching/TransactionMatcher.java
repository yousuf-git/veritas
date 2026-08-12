package com.reconengine.recon.matching;

import com.reconengine.common.Money;
import com.reconengine.config.AppProperties;
import com.reconengine.ledger.LedgerEntry;
import com.reconengine.ledger.LedgerEntryRepository;
import com.reconengine.ledger.LedgerEntryType;
import com.reconengine.recon.DiscrepancyType;
import com.reconengine.recon.MatchResult;
import com.reconengine.recon.MatchStage;
import com.reconengine.recon.Severity;
import com.reconengine.settlement.SettlementLine;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Ties one provider row to the internal ledger, degrading through three stages: an exact
 * provider-reference match, then a scored heuristic on amount, date and description, then
 * giving up and handing the line to a human with a classified reason.
 * <p>
 * Every branch records why, because "unmatched" without a reason is not actionable for the
 * finance team working the queue.
 */
@Component
public class TransactionMatcher {

    /** Amount contributes most: a settlement that disagrees on money is not the same event. */
    private static final double WEIGHT_AMOUNT = 0.6;
    private static final double WEIGHT_TIME = 0.3;
    private static final double WEIGHT_REFERENCE = 0.1;

    /** Above this, an amount difference is treated as a real drift rather than a rounding artefact. */
    private static final long HIGH_SEVERITY_IMPACT_MINOR = 1_000L;

    private static final int MAX_CANDIDATES = 50;

    private final LedgerEntryRepository ledgerEntries;
    private final AppProperties properties;

    public TransactionMatcher(LedgerEntryRepository ledgerEntries, AppProperties properties) {
        this.ledgerEntries = ledgerEntries;
        this.properties = properties;
    }

    public LineOutcome match(SettlementLine line, RunMatchingContext context) {
        LineKind kind = LineKind.of(line);

        if (kind == LineKind.EXCLUDED) {
            return new LineOutcome(
                    MatchResult.excluded(context.runId(), line.getId(),
                            "'%s' rows move money between balances and are not reconciled against the ledger."
                                    .formatted(line.getTxnType())),
                    List.of());
        }

        if (kind == LineKind.FEE) {
            return matchStandaloneFee(line, context);
        }

        LedgerEntryType expectedType = kind.ledgerEntryType();

        // A reference that resolves to an entry another line already claimed falls out of
        // settle() as a duplicate charge rather than being matched twice.
        Optional<LedgerEntry> exact = findExact(line, expectedType);
        if (exact.isPresent()) {
            return settle(line, exact.get(), MatchStage.EXACT, 1.0, context,
                    "Provider reference %s matches ledger entry %s exactly."
                            .formatted(line.getProviderRef(), exact.get().getExternalRef()));
        }

        Optional<Scored> heuristic = findHeuristic(line, expectedType, context);
        if (heuristic.isPresent()) {
            Scored best = heuristic.get();
            return settle(line, best.entry(), MatchStage.HEURISTIC, best.score(), context, best.reason());
        }

        return missingLedgerEntry(line, context, kind);
    }

    private Optional<LedgerEntry> findExact(SettlementLine line, LedgerEntryType expectedType) {
        if (!hasProviderRef(line)) {
            return Optional.empty();
        }
        return ledgerEntries
                .findByProviderRefAndCurrencyAndEntryType(line.getProviderRef(), line.getCurrency(), expectedType)
                .stream()
                .findFirst();
    }

    private Optional<Scored> findHeuristic(SettlementLine line, LedgerEntryType expectedType,
                                           RunMatchingContext context) {
        long tolerance = properties.matching().amountToleranceMinor();
        Duration window = properties.matching().dateWindow();
        long gross = line.getGrossMinor();

        List<LedgerEntry> candidates = ledgerEntries.findHeuristicCandidates(
                expectedType,
                line.getCurrency(),
                Math.subtractExact(gross, tolerance),
                Math.addExact(gross, tolerance),
                line.getCreatedAtProvider().minus(window),
                line.getCreatedAtProvider().plus(window),
                PageRequest.of(0, MAX_CANDIDATES));

        return candidates.stream()
                .filter(candidate -> !context.isClaimed(candidate.getId()))
                .map(candidate -> score(line, candidate, window))
                .filter(scored -> scored.score() >= properties.matching().minHeuristicConfidence())
                .max(Comparator.comparingDouble(Scored::score)
                        .thenComparing(Comparator.comparingLong(Scored::timeDistanceSeconds).reversed())
                        .thenComparing(scored -> scored.entry().getId()));
    }

    private Scored score(SettlementLine line, LedgerEntry candidate, Duration window) {
        long delta = line.getGrossMinor() - candidate.getAmountMinor();
        double amountScore = delta == 0 ? 1.0 : 0.8;

        long distanceSeconds = Math.abs(Duration.between(
                candidate.getOccurredAt(), line.getCreatedAtProvider()).toSeconds());
        double timeScore = window.isZero() ? 0d
                : Math.max(0d, 1.0 - (double) distanceSeconds / window.toSeconds());

        boolean referenceMentioned = mentionsReference(line, candidate);
        double referenceScore = referenceMentioned ? 1.0 : 0d;

        double score = amountScore * WEIGHT_AMOUNT + timeScore * WEIGHT_TIME + referenceScore * WEIGHT_REFERENCE;

        String reason = ("No provider reference; matched on amount %s, %s apart, description %s ledger "
                + "reference %s (confidence %.2f).").formatted(
                line.getGross(),
                Duration.ofSeconds(distanceSeconds),
                referenceMentioned ? "mentions" : "does not mention",
                candidate.getExternalRef(),
                score);

        return new Scored(candidate, score, distanceSeconds, reason);
    }

    private boolean mentionsReference(SettlementLine line, LedgerEntry candidate) {
        String description = line.getDescription();
        return description != null
                && description.toLowerCase(Locale.ROOT).contains(candidate.getExternalRef().toLowerCase(Locale.ROOT));
    }

    /**
     * Records the match and, when the amounts disagree, the discrepancy that explains the gap.
     * Also checks the fee the provider deducted against the fee we booked.
     */
    private LineOutcome settle(SettlementLine line, LedgerEntry entry, MatchStage stage, double confidence,
                               RunMatchingContext context, String reason) {
        if (!context.claim(entry.getId())) {
            return duplicate(line, context);
        }

        long delta = line.getGrossMinor() - entry.getAmountMinor();
        List<LineOutcome.PendingDiscrepancy> discrepancies = new ArrayList<>();

        if (delta != 0) {
            discrepancies.add(amountDifference(line, entry, delta));
        }

        feeDiscrepancy(line, entry).ifPresent(discrepancies::add);

        String fullReason = delta == 0 ? reason
                : reason + " Amount differs by " + Money.of(delta, line.getCurrency()) + ".";

        return new LineOutcome(
                MatchResult.matched(context.runId(), line.getId(), entry.getId(), stage, confidence, delta,
                        truncate(fullReason)),
                List.copyOf(discrepancies));
    }

    private LineOutcome.PendingDiscrepancy amountDifference(SettlementLine line, LedgerEntry entry, long delta) {
        long tolerance = properties.matching().amountToleranceMinor();
        boolean withinTolerance = Math.abs(delta) <= tolerance;

        DiscrepancyType type = withinTolerance ? DiscrepancyType.FX_ROUNDING : DiscrepancyType.AMOUNT_DRIFT;
        Severity severity = withinTolerance ? Severity.LOW
                : Math.abs(delta) >= HIGH_SEVERITY_IMPACT_MINOR ? Severity.HIGH : Severity.MEDIUM;

        String detail = withinTolerance
                ? "Provider settled %s against ledger %s, a %s difference within the %d minor unit rounding tolerance."
                .formatted(line.getGross(), entry.getAmount(), Money.of(delta, line.getCurrency()), tolerance)
                : "Provider settled %s but the ledger records %s for %s, a difference of %s."
                .formatted(line.getGross(), entry.getAmount(), entry.getExternalRef(),
                        Money.of(delta, line.getCurrency()));

        return new LineOutcome.PendingDiscrepancy(type, severity, Money.of(delta, line.getCurrency()),
                truncate(detail), line.getId(), entry.getId());
    }

    /**
     * A fee is verified against the FEE ledger entry booked for the same charge. The key comes
     * from the matched entry rather than the line, so this still works for a line that arrived
     * without any provider reference.
     */
    private Optional<LineOutcome.PendingDiscrepancy> feeDiscrepancy(SettlementLine line, LedgerEntry entry) {
        if (line.getFeeMinor() == 0) {
            return Optional.empty();
        }

        String feeKey = hasProviderRef(line) ? line.getProviderRef() : entry.getProviderRef();
        if (feeKey == null) {
            return Optional.empty();
        }

        List<LedgerEntry> bookedFees = ledgerEntries.findByProviderRefAndCurrencyAndEntryType(
                feeKey, line.getCurrency(), LedgerEntryType.FEE);

        if (bookedFees.isEmpty()) {
            return Optional.of(new LineOutcome.PendingDiscrepancy(
                    DiscrepancyType.UNEXPECTED_FEE,
                    Math.abs(line.getFeeMinor()) >= HIGH_SEVERITY_IMPACT_MINOR ? Severity.HIGH : Severity.MEDIUM,
                    Money.of(-line.getFeeMinor(), line.getCurrency()),
                    truncate("Provider deducted a fee of %s on %s but no fee was booked against %s."
                            .formatted(line.getFee(), line.getProviderTxnId(), feeKey)),
                    line.getId(),
                    entry.getId()));
        }

        // Fees are booked as negative amounts, so the provider's positive deduction is negated.
        long bookedMinor = bookedFees.stream().mapToLong(LedgerEntry::getAmountMinor).sum();
        long feeDelta = -line.getFeeMinor() - bookedMinor;
        if (feeDelta == 0) {
            return Optional.empty();
        }

        return Optional.of(new LineOutcome.PendingDiscrepancy(
                DiscrepancyType.UNEXPECTED_FEE,
                Math.abs(feeDelta) >= HIGH_SEVERITY_IMPACT_MINOR ? Severity.HIGH : Severity.MEDIUM,
                Money.of(feeDelta, line.getCurrency()),
                truncate("Provider deducted %s but %s was booked against %s, a difference of %s."
                        .formatted(line.getFee(), Money.of(-bookedMinor, line.getCurrency()), feeKey,
                                Money.of(feeDelta, line.getCurrency()))),
                line.getId(),
                entry.getId()));
    }

    /** A fee line with no charge of its own: reconciled directly against a booked FEE entry. */
    private LineOutcome matchStandaloneFee(SettlementLine line, RunMatchingContext context) {
        Optional<LedgerEntry> booked = findExact(line, LedgerEntryType.FEE);

        if (booked.isPresent()) {
            return settle(line, booked.get(), MatchStage.EXACT, 1.0, context,
                    "Fee line matches booked fee %s.".formatted(booked.get().getExternalRef()));
        }

        return new LineOutcome(
                MatchResult.unmatched(context.runId(), line.getId(),
                        truncate("Provider charged a '%s' fee of %s with no corresponding fee in the ledger."
                                .formatted(line.getTxnType(), line.getGross()))),
                List.of(new LineOutcome.PendingDiscrepancy(
                        DiscrepancyType.UNEXPECTED_FEE,
                        Math.abs(line.getGrossMinor()) >= HIGH_SEVERITY_IMPACT_MINOR ? Severity.HIGH : Severity.MEDIUM,
                        line.getGross(),
                        truncate("Unbooked '%s' fee of %s on %s."
                                .formatted(line.getTxnType(), line.getGross(), line.getProviderTxnId())),
                        line.getId(),
                        null)));
    }

    private LineOutcome duplicate(SettlementLine line, RunMatchingContext context) {
        String source = hasProviderRef(line) ? line.getProviderRef() : line.getProviderTxnId();
        String detail = "Source %s was already settled earlier in this file; %s settles it a second time for %s."
                .formatted(source, line.getProviderTxnId(), line.getGross());

        return new LineOutcome(
                MatchResult.unmatched(context.runId(), line.getId(), truncate(detail)),
                List.of(new LineOutcome.PendingDiscrepancy(
                        DiscrepancyType.DUPLICATE_CHARGE,
                        Severity.HIGH,
                        line.getGross(),
                        truncate(detail),
                        line.getId(),
                        null)));
    }

    private LineOutcome missingLedgerEntry(SettlementLine line, RunMatchingContext context, LineKind kind) {
        String detail = ("Provider settled %s (%s, %s) on %s but the ledger has no matching %s within the "
                + "%s window.").formatted(
                line.getGross(),
                line.getProviderTxnId(),
                line.getProviderRef() == null ? "no source reference" : line.getProviderRef(),
                line.getCreatedAtProvider(),
                kind.ledgerEntryType(),
                properties.matching().dateWindow());

        return new LineOutcome(
                MatchResult.unmatched(context.runId(), line.getId(), truncate(detail)),
                List.of(new LineOutcome.PendingDiscrepancy(
                        DiscrepancyType.MISSING_LEDGER_ENTRY,
                        Math.abs(line.getGrossMinor()) >= HIGH_SEVERITY_IMPACT_MINOR ? Severity.HIGH : Severity.MEDIUM,
                        line.getGross(),
                        truncate(detail),
                        line.getId(),
                        null)));
    }

    private boolean hasProviderRef(SettlementLine line) {
        return line.getProviderRef() != null && !line.getProviderRef().isBlank();
    }

    private static String truncate(String value) {
        return value.length() <= 512 ? value : value.substring(0, 509) + "...";
    }

    private record Scored(LedgerEntry entry, double score, long timeDistanceSeconds, String reason) {
    }

    /**
     * Maps a provider's reporting category onto what the ledger should hold. Stripe's categories
     * are stable enough to switch on; anything unrecognised falls back to the sign of the amount.
     */
    private enum LineKind {
        CHARGE(LedgerEntryType.ORDER),
        REFUND(LedgerEntryType.REFUND),
        FEE(LedgerEntryType.FEE),
        EXCLUDED(null);

        private final LedgerEntryType ledgerEntryType;

        LineKind(LedgerEntryType ledgerEntryType) {
            this.ledgerEntryType = ledgerEntryType;
        }

        LedgerEntryType ledgerEntryType() {
            return ledgerEntryType;
        }

        static LineKind of(SettlementLine line) {
            String category = line.getTxnType() == null ? "" : line.getTxnType().toLowerCase(Locale.ROOT);
            return switch (category) {
                case "charge", "payment", "capture" -> CHARGE;
                case "refund", "payment_refund", "partial_capture_reversal", "payment_failure_refund" -> REFUND;
                case "fee", "stripe_fee", "billing_fee", "connect_fee", "network_cost" -> FEE;
                case "payout", "payout_reversal", "transfer", "topup", "platform_earning" -> EXCLUDED;
                default -> line.getGrossMinor() >= 0 ? CHARGE : REFUND;
            };
        }
    }
}
