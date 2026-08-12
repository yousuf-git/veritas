package com.reconengine.recon;

/** How a settlement line was tied to the ledger, in the order the pipeline attempts them. */
public enum MatchStage {

    /** Provider reference identical to a ledger entry's provider reference. */
    EXACT,

    /** No shared reference; matched on amount, date proximity and description instead. */
    HEURISTIC,

    /** A person linked it from the exception queue. */
    MANUAL,

    /** No link was made. */
    NONE
}
