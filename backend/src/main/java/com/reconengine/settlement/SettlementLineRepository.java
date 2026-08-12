package com.reconengine.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SettlementLineRepository extends JpaRepository<SettlementLine, UUID> {

    int countByFileId(UUID fileId);

    /** Also the batch reader's source; the reader resolves it by name and appends the Pageable. */
    Page<SettlementLine> findByFileIdOrderByLineNumber(UUID fileId, Pageable pageable);

    /** Bounds the ledger window a run needs to consider, derived from the file itself. */
    @Query("""
            select min(l.createdAtProvider) as earliest, max(l.createdAtProvider) as latest
            from SettlementLine l
            where l.fileId = :fileId
            """)
    DateRange findDateRange(@Param("fileId") UUID fileId);

    @Query("select distinct l.currency from SettlementLine l where l.fileId = :fileId")
    List<String> findCurrencies(@Param("fileId") UUID fileId);

    interface DateRange {
        Instant getEarliest();

        Instant getLatest();
    }
}
