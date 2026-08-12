package com.reconengine.exceptionqueue;

import com.reconengine.user.Role;

/** What a finance user can do with an exception, and the permission each action demands. */
public enum ResolutionAction {

    /** The provider's fee was legitimate; book it and close the item. */
    ACCEPT_FEE(Role.Permissions.EXCEPTION_RESOLVE),

    /** Attach the settlement line to a ledger entry the matcher could not find. */
    LINK_MANUALLY(Role.Permissions.EXCEPTION_RESOLVE),

    /** Hand the item to someone with more authority; it stays open. */
    ESCALATE(Role.Permissions.EXCEPTION_RESOLVE),

    /** Accept the loss. Reserved for approvers, because it writes money off the books. */
    WRITE_OFF(Role.Permissions.EXCEPTION_WRITE_OFF),

    /** Not a real discrepancy; close it without any money movement. */
    REJECT(Role.Permissions.EXCEPTION_RESOLVE);

    private final String requiredPermission;

    ResolutionAction(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }

    public String requiredPermission() {
        return requiredPermission;
    }
}
