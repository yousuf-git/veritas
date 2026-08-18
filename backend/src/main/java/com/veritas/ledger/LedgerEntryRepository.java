package com.veritas.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Optional<LedgerEntry> findByEntryTypeAndExternalRef(LedgerEntryType entryType, String externalRef);

    List<LedgerEntry> findByProviderRefAndCurrency(String providerRef, String currency);

    List<LedgerEntry> findByProviderRefAndCurrencyAndEntryType(String providerRef, String currency,
                                                              LedgerEntryType entryType);

    /**
     * Candidates for heuristic matching: right type, right currency, amount inside the tolerance
     * band and date inside the window. Bounded by the caller so a pathological window cannot
     * pull the whole ledger into memory.
     */
    @Query("""
            select e from LedgerEntry e
            where e.entryType = :entryType
              and e.currency = :currency
              and e.amountMinor between :minAmount and :maxAmount
              and e.occurredAt between :from and :to
            order by e.occurredAt
            """)
    List<LedgerEntry> findHeuristicCandidates(@Param("entryType") LedgerEntryType entryType,
                                              @Param("currency") String currency,
                                              @Param("minAmount") long minAmount,
                                              @Param("maxAmount") long maxAmount,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to,
                                              Pageable pageable);

    /**
     * Ledger entries the provider never settled in this run. Fees are excluded on purpose: a fee
     * is reconciled against the fee column of its charge's line, not as a line of its own.
     */
    @Query("""
            select e from LedgerEntry e
            where e.entryType in :entryTypes
              and e.currency in :currencies
              and e.occurredAt between :from and :to
              and not exists (
                  select 1 from MatchResult m
                  where m.runId = :runId and m.ledgerEntryId = e.id
              )
            order by e.occurredAt
            """)
    Page<LedgerEntry> findUnsettled(@Param("runId") UUID runId,
                                    @Param("entryTypes") Collection<LedgerEntryType> entryTypes,
                                    @Param("currencies") Collection<String> currencies,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    @Query("select e.externalRef from LedgerEntry e where e.externalRef in :externalRefs")
    List<String> findExistingExternalRefs(@Param("externalRefs") Collection<String> externalRefs);

    @Query("""
            select e from LedgerEntry e
            where (:entryType is null or e.entryType = :entryType)
              and (:currency is null or e.currency = :currency)
              and (:externalRef is null or e.externalRef = :externalRef)
              and (:providerRef is null or e.providerRef = :providerRef)
              and (cast(:from as Instant) is null or e.occurredAt >= :from)
              and (cast(:to as Instant) is null or e.occurredAt <= :to)
            """)
    Page<LedgerEntry> search(@Param("entryType") LedgerEntryType entryType,
                            @Param("currency") String currency,
                            @Param("externalRef") String externalRef,
                            @Param("providerRef") String providerRef,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);
}
