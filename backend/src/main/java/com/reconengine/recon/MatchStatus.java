package com.reconengine.recon;

public enum MatchStatus {

    /** Linked to a ledger entry and the amounts agree exactly. */
    MATCHED,

    /** Linked, but the amounts differ; a discrepancy carries the detail. */
    PARTIAL,

    /** No ledger entry could be found for this provider row. */
    UNMATCHED,

    /** Not a customer transaction, so never expected to appear in the ledger (e.g. a payout). */
    EXCLUDED
}
