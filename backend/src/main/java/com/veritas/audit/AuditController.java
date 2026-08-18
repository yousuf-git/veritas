package com.veritas.audit;

import com.veritas.common.PageResponse;
import com.veritas.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit trail")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + Role.Permissions.AUDIT_READ + "')")
    @Operation(summary = "Read the audit trail, optionally scoped to one entity")
    public PageResponse<AuditResponse> read(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {

        PageRequest pageable = PageRequest.of(page, size);
        var results = entityType != null && entityId != null
                ? audit.forEntity(entityType, entityId, pageable)
                : audit.recent(pageable);

        return PageResponse.from(results, AuditResponse::from);
    }

    public record AuditResponse(
            long id,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            Map<String, String> detail,
            Instant occurredAt) {

        public static AuditResponse from(AuditLog log) {
            return new AuditResponse(
                    log.getId(),
                    log.getActorUsername(),
                    log.getAction(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.getDetail(),
                    log.getOccurredAt());
        }
    }
}
