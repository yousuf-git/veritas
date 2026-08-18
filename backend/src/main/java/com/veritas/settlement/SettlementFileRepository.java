package com.veritas.settlement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementFileRepository extends JpaRepository<SettlementFile, UUID> {

    Optional<SettlementFile> findByChecksumSha256(String checksumSha256);

    Page<SettlementFile> findAllByOrderByUploadedAtDesc(Pageable pageable);
}
