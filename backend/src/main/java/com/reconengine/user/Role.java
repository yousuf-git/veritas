package com.reconengine.user;

import java.util.Set;

/**
 * Roles map to fine-grained permissions here rather than being checked directly, so an
 * endpoint asserts the capability it needs ("exception:write_off") and the role table stays
 * the only place that decides who has it.
 */
public enum Role {

    FINANCE_ANALYST(Set.of(
            Permissions.LEDGER_READ,
            Permissions.FILE_READ,
            Permissions.FILE_UPLOAD,
            Permissions.RUN_READ,
            Permissions.RUN_TRIGGER,
            Permissions.EXCEPTION_READ,
            Permissions.EXCEPTION_RESOLVE,
            Permissions.AUDIT_READ)),

    FINANCE_APPROVER(Set.of(
            Permissions.LEDGER_READ,
            Permissions.FILE_READ,
            Permissions.FILE_UPLOAD,
            Permissions.RUN_READ,
            Permissions.RUN_TRIGGER,
            Permissions.EXCEPTION_READ,
            Permissions.EXCEPTION_RESOLVE,
            Permissions.EXCEPTION_WRITE_OFF,
            Permissions.AUDIT_READ)),

    ADMIN(Set.of(
            Permissions.LEDGER_READ,
            Permissions.LEDGER_WRITE,
            Permissions.FILE_READ,
            Permissions.FILE_UPLOAD,
            Permissions.RUN_READ,
            Permissions.RUN_TRIGGER,
            Permissions.EXCEPTION_READ,
            Permissions.EXCEPTION_RESOLVE,
            Permissions.EXCEPTION_WRITE_OFF,
            Permissions.AUDIT_READ));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public static final class Permissions {
        public static final String LEDGER_READ = "ledger:read";
        public static final String LEDGER_WRITE = "ledger:write";
        public static final String FILE_READ = "file:read";
        public static final String FILE_UPLOAD = "file:upload";
        public static final String RUN_READ = "run:read";
        public static final String RUN_TRIGGER = "run:trigger";
        public static final String EXCEPTION_READ = "exception:read";
        public static final String EXCEPTION_RESOLVE = "exception:resolve";
        public static final String EXCEPTION_WRITE_OFF = "exception:write_off";
        public static final String AUDIT_READ = "audit:read";

        private Permissions() {
        }
    }
}
