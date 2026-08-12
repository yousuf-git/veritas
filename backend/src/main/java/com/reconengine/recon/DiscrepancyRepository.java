package com.reconengine.recon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, UUID> {

    int countByRunId(UUID runId);

    @Query("""
            select d from Discrepancy d
            where (:runId is null or d.runId = :runId)
              and (:status is null or d.status = :status)
              and (:type is null or d.type = :type)
              and (:severity is null or d.severity = :severity)
            """)
    Page<Discrepancy> search(@Param("runId") UUID runId,
                             @Param("status") DiscrepancyStatus status,
                             @Param("type") DiscrepancyType type,
                             @Param("severity") Severity severity,
                             Pageable pageable);

    @Query("""
            select d.type as type, d.severity as severity, count(d) as count,
                   coalesce(sum(abs(d.amountImpactMinor)), 0) as amountMinor
            from Discrepancy d
            where d.runId = :runId
            group by d.type, d.severity
            """)
    List<TypeBreakdown> breakdownForRun(@Param("runId") UUID runId);

    interface TypeBreakdown {
        DiscrepancyType getType();

        Severity getSeverity();

        long getCount();

        long getAmountMinor();
    }
}
