package com.reconengine.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

/**
 * An exact monetary amount held as minor units (cents, pence, yen) plus an ISO-4217 code.
 * Floating point never touches money in this service; {@link #toMajorUnits()} exists only
 * for presentation and uses the currency's own fraction digits, so JPY renders as 100 and
 * USD as 1.00 from the same 100 minor units.
 */
public record Money(long minorUnits, String currency) implements Comparable<Money> {

    private static final Pattern ISO_4217 = Pattern.compile("^[A-Z]{3}$");

    public Money {
        if (currency == null || !ISO_4217.matcher(currency).matches()) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO-4217 code, got: " + currency);
        }
    }

    public static Money of(long minorUnits, String currency) {
        return new Money(minorUnits, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public Money abs() {
        return minorUnits < 0 ? negate() : this;
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public int signum() {
        return Long.signum(minorUnits);
    }

    /** Absolute difference, used by the matcher to measure amount drift. */
    public long absoluteDifference(Money other) {
        requireSameCurrency(other);
        return Math.abs(Math.subtractExact(minorUnits, other.minorUnits));
    }

    public BigDecimal toMajorUnits() {
        return BigDecimal.valueOf(minorUnits, fractionDigits(currency));
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    @Override
    public String toString() {
        return toMajorUnits().toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    /**
     * Falls back to 2 digits for codes the JVM does not know, which keeps parsing of an
     * unfamiliar provider currency from blowing up the whole file.
     */
    public static int fractionDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? 2 : digits;
        } catch (IllegalArgumentException ex) {
            return 2;
        }
    }

    public static class CurrencyMismatchException extends IllegalArgumentException {
        public CurrencyMismatchException(String left, String right) {
            super("cannot combine amounts in " + left + " and " + right);
        }
    }
}
