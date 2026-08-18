package com.veritas.provider.generator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ScenarioRequest(
        @Min(1) @Max(20_000) int transactions,
        long seed,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(0) @Max(1000) int missingPayouts,
        @Min(0) @Max(1000) int missingLedgerEntries,
        @Min(0) @Max(1000) int amountDrifts,
        @Min(0) @Max(1000) int fxRoundings,
        @Min(0) @Max(1000) int duplicateCharges,
        @Min(0) @Max(1000) int unexpectedFees,
        /* Rows delivered without a provider reference, which must still be matched heuristically. */
        @Min(0) @Max(1000) int heuristicOnly) {

    public ScenarioRequest {
        if (currency == null || currency.isBlank()) {
            currency = "USD";
        }
        if (transactions == 0) {
            transactions = 500;
        }
    }

    public int totalDefects() {
        return missingPayouts + missingLedgerEntries + amountDrifts + fxRoundings
                + duplicateCharges + unexpectedFees;
    }

    /** Transactions reserved for a specific behaviour, defective or otherwise. */
    public int reservedTransactions() {
        return totalDefects() + heuristicOnly;
    }

    /** A balanced default: enough volume to be interesting, enough defects to fill a queue. */
    public static ScenarioRequest defaults(long seed) {
        return new ScenarioRequest(500, seed, "USD", 6, 4, 5, 8, 3, 4, 25);
    }
}
