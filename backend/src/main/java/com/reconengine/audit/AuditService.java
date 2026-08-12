package com.reconengine.audit;

import com.reconengine.auth.CurrentActor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Writes inside the caller's transaction on purpose: if the action it describes rolls back,
 * the trail must not claim it happened.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void record(CurrentActor.Actor actor, String action, String entityType, Object entityId,
                       Map<String, String> detail) {
        auditLogs.save(new AuditLog(actor.id(), actor.username(), action, entityType,
                String.valueOf(entityId), detail));
    }

    /** For work started by the engine itself rather than a person, such as a scheduled run. */
    @Transactional
    public void recordSystem(String action, String entityType, Object entityId, Map<String, String> detail) {
        auditLogs.save(new AuditLog(null, "system", action, entityType, String.valueOf(entityId), detail));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> forEntity(String entityType, UUID entityId, Pageable pageable) {
        return auditLogs.findByEntityTypeAndEntityIdOrderByOccurredAtDesc(entityType, entityId.toString(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> recent(Pageable pageable) {
        return auditLogs.findAllByOrderByOccurredAtDesc(pageable);
    }
}
