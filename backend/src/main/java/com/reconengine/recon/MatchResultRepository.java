package com.reconengine.recon;

import com.reconengine.settlement.SettlementLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface MatchResultRepository extends JpaRepository<MatchResult, UUID> {

    int countByRunId(UUID runId);

    int countByRunIdAndMatchStage(UUID runId, MatchStage stage);

    int countByRunIdAndMatchStatus(UUID runId, MatchStatus status);

    Optional<MatchResult> findByRunIdAndSettlementLineId(UUID runId, UUID settlementLineId);

    @Query("""
            select coalesce(sum(abs(line.grossMinor)), 0)
            from MatchResult m
            join SettlementLine line on line.id = m.settlementLineId
            where m.runId = :runId and m.matchStatus in :statuses
            """)
    long sumLineAmountsByStatus(@Param("runId") UUID runId,
                                @Param("statuses") Collection<MatchStatus> statuses);

    @Query("""
            select line
            from MatchResult m
            join SettlementLine line on line.id = m.settlementLineId
            where m.runId = :runId and m.matchStatus in :statuses
            order by line.lineNumber
            """)
    Page<SettlementLine> findLinesByStatus(@Param("runId") UUID runId,
                                           @Param("statuses") Collection<MatchStatus> statuses,
                                           Pageable pageable);
}
