package com.veritas.ledger;

/**
 * Sign convention, enforced by a database CHECK constraint: money coming in is positive,
 * money going out is negative.
 */
public enum LedgerEntryType {

    /** A customer payment we expect the provider to settle to us. Positive. */
    ORDER,

    /** Money returned to a customer. Negative. */
    REFUND,

    /** A fee we expect to be deducted from a payout. Negative. */
    FEE,

    /** A manual correction; either sign. */
    ADJUSTMENT
}
