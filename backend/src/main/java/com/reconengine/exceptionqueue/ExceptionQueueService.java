package com.reconengine.exceptionqueue;

import com.reconengine.audit.AuditService;
import com.reconengine.auth.CurrentActor;
import com.reconengine.common.Errors;
import com.reconengine.ledger.LedgerEntry;
import com.reconengine.ledger.LedgerEntryRepository;
import com.reconengine.recon.Discrepancy;
import com.reconengine.recon.DiscrepancyRepository;
import com.reconengine.recon.DiscrepancyStatus;
import com.reconengine.recon.DiscrepancyType;
import com.reconengine.recon.MatchResult;
import com.reconengine.recon.MatchResultRepository;
import com.reconengine.recon.Severity;
import com.reconengine.settlement.SettlementLine;
import com.reconengine.settlement.SettlementLineRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExceptionQueueService {

    private final DiscrepancyRepository discrepancies;
    private final DiscrepancyResolutionRepository resolutions;
    private final MatchResultRepository matchResults;
    private final LedgerEntryRepository ledgerEntries;
    private final SettlementLineRepository settlementLines;
    private final AuditService audit;
    private final CurrentActor currentActor;
    private final Clock clock;

    public ExceptionQueueService(DiscrepancyRepository discrepancies, DiscrepancyResolutionRepository resolutions,
                                 MatchResultRepository matchResults, LedgerEntryRepository ledgerEntries,
                                 SettlementLineRepository settlementLines, AuditService audit,
                                 CurrentActor currentActor, Clock clock) {
        this.discrepancies = discrepancies;
        this.resolutions = resolutions;
        this.matchResults = matchResults;
        this.ledgerEntries = ledgerEntries;
        this.settlementLines = settlementLines;
        this.audit = audit;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<Discrepancy> search(UUID runId, DiscrepancyStatus status, DiscrepancyType type,
                                    Severity severity, Pageable pageable) {
        return discrepancies.search(runId, status, type, severity, pageable);
    }

    @Transactional(readOnly = true)
    public Discrepancy get(UUID id) {
        return discrepancies.findById(id).orElseThrow(() -> new Errors.NotFound("Discrepancy", id));
    }

    @Transactional(readOnly = true)
    public List<DiscrepancyResolution> historyOf(UUID discrepancyId) {
        get(discrepancyId);
        return resolutions.findByDiscrepancyIdOrderByResolvedAtDesc(discrepancyId);
    }

    @Transactional(readOnly = true)
    public Optional<SettlementLine> settlementLineOf(Discrepancy discrepancy) {
        return discrepancy.getSettlementLineId() == null
                ? Optional.empty()
                : settlementLines.findById(discrepancy.getSettlementLineId());
    }

    @Transactional(readOnly = true)
    public Optional<LedgerEntry> ledgerEntryOf(Discrepancy discrepancy) {
        return discrepancy.getLedgerEntryId() == null
                ? Optional.empty()
                : ledgerEntries.findById(discrepancy.getLedgerEntryId());
    }

    @Transactional
    public Discrepancy claim(UUID discrepancyId, Integer expectedVersion) {
        Discrepancy discrepancy = requireOpen(discrepancyId, expectedVersion);
        CurrentActor.Actor actor = currentActor.require();

        discrepancy.takeForReview(actor.id());
        discrepancies.save(discrepancy);

        audit.record(actor, "EXCEPTION_CLAIMED", "Discrepancy", discrepancyId,
                Map.of("type", discrepancy.getType().name()));
        return discrepancy;
    }

    /**
     * Applies a decision and records it. The permission demanded depends on the action, so
     * writing money off needs an approver even though every analyst can resolve the rest.
     */
    @Transactional
    public Discrepancy resolve(UUID discrepancyId, ResolutionAction action, String note,
                               UUID linkedLedgerEntryId, Integer expectedVersion) {

        requirePermission(action);
        Discrepancy discrepancy = requireOpen(discrepancyId, expectedVersion);
        CurrentActor.Actor actor = currentActor.require();

        if (action == ResolutionAction.LINK_MANUALLY) {
            linkManually(discrepancy, linkedLedgerEntryId);
        } else if (linkedLedgerEntryId != null) {
            throw new Errors.BadRequest("UNEXPECTED_LINK",
                    "A ledger entry can only be supplied with the LINK_MANUALLY action.");
        }

        if (action == ResolutionAction.ESCALATE) {
            discrepancy.escalate();
        } else {
            discrepancy.resolve(clock.instant());
        }
        discrepancies.save(discrepancy);

        resolutions.save(new DiscrepancyResolution(discrepancyId, action, linkedLedgerEntryId, note,
                actor.id(), actor.username()));

        Map<String, String> detail = new HashMap<>(Map.of(
                "action", action.name(),
                "type", discrepancy.getType().name(),
                "amountImpact", discrepancy.getAmountImpact().toString(),
                "note", note));
        if (linkedLedgerEntryId != null) {
            detail.put("linkedLedgerEntryId", linkedLedgerEntryId.toString());
        }
        audit.record(actor, "EXCEPTION_RESOLVED", "Discrepancy", discrepancyId, detail);

        return discrepancy;
    }

    /**
     * Rewrites the line's match result to point at the entry the analyst chose. The database's
     * partial unique index still guarantees one ledger entry backs at most one line per run, so
     * linking to an entry another line already uses is rejected rather than double-booked.
     */
    private void linkManually(Discrepancy discrepancy, UUID linkedLedgerEntryId) {
        if (linkedLedgerEntryId == null) {
            throw new Errors.BadRequest("MISSING_LINK",
                    "LINK_MANUALLY requires the ledger entry to link to.");
        }
        if (discrepancy.getSettlementLineId() == null) {
            throw new Errors.Unprocessable("NOT_LINKABLE",
                    "This exception is about a ledger entry with no settlement line, so there is nothing to link.");
        }

        LedgerEntry entry = ledgerEntries.findById(linkedLedgerEntryId)
                .orElseThrow(() -> new Errors.NotFound("Ledger entry", linkedLedgerEntryId));
        SettlementLine line = settlementLines.findById(discrepancy.getSettlementLineId())
                .orElseThrow(() -> new Errors.NotFound("Settlement line", discrepancy.getSettlementLineId()));

        if (!entry.getCurrency().equals(line.getCurrency())) {
            throw new Errors.Unprocessable("CURRENCY_MISMATCH",
                    "Cannot link a %s ledger entry to a %s settlement line."
                            .formatted(entry.getCurrency(), line.getCurrency()));
        }

        MatchResult result = matchResults
                .findByRunIdAndSettlementLineId(discrepancy.getRunId(), discrepancy.getSettlementLineId())
                .orElseThrow(() -> new Errors.NotFound("Match result for settlement line",
                        discrepancy.getSettlementLineId()));

        long delta = line.getGrossMinor() - entry.getAmountMinor();
        result.linkManually(linkedLedgerEntryId, delta,
                "Linked by hand to ledger entry " + entry.getExternalRef() + ".");

        try {
            matchResults.saveAndFlush(result);
        } catch (DataIntegrityViolationException ex) {
            throw new Errors.Conflict("LEDGER_ENTRY_ALREADY_MATCHED",
                    "Ledger entry %s is already matched to another settlement line in this run."
                            .formatted(entry.getExternalRef()));
        }
    }

    private Discrepancy requireOpen(UUID discrepancyId, Integer expectedVersion) {
        Discrepancy discrepancy = get(discrepancyId);

        if (expectedVersion != null && expectedVersion != discrepancy.getVersion()) {
            throw new Errors.Conflict("STALE_RECORD",
                    "This exception changed since you loaded it. Reload and try again.");
        }
        if (discrepancy.isClosed()) {
            throw new Errors.Conflict("ALREADY_RESOLVED", "This exception is already resolved.");
        }
        return discrepancy;
    }

    private void requirePermission(ResolutionAction action) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allowed = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(action.requiredPermission()::equals);

        if (!allowed) {
            throw new Errors.Forbidden(
                    "Action %s requires the '%s' permission.".formatted(action, action.requiredPermission()));
        }
    }
}
