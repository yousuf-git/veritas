package com.reconengine.recon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    boolean existsByFileIdAndStatusIn(UUID fileId, Collection<RunStatus> statuses);

    List<ReconciliationRun> findByFileIdOrderByStartedAtDesc(UUID fileId);

    Page<ReconciliationRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}
