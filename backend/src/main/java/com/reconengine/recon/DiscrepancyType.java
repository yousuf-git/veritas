package com.reconengine.recon;

public enum DiscrepancyType {

    /** We booked the money; the provider never settled it. */
    MISSING_PAYOUT,

    /** The provider settled money we have no ledger entry for. */
    MISSING_LEDGER_ENTRY,

    /** Both sides agree the transaction exists but disagree on the amount. */
    AMOUNT_DRIFT,

    /** The same source object was settled more than once in one file. */
    DUPLICATE_CHARGE,

    /** The provider deducted a fee that was never booked, or booked at a different amount. */
    UNEXPECTED_FEE,

    /** A sub-tolerance amount difference consistent with currency rounding. */
    FX_ROUNDING
}
