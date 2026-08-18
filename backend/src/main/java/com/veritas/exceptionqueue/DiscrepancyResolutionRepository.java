package com.veritas.exceptionqueue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiscrepancyResolutionRepository extends JpaRepository<DiscrepancyResolution, UUID> {

    List<DiscrepancyResolution> findByDiscrepancyIdOrderByResolvedAtDesc(UUID discrepancyId);
}
