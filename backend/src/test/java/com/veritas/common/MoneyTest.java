package com.veritas.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("renders minor units using the currency's own fraction digits")
    void rendersUsingCurrencyFractionDigits() {
        assertThat(Money.of(12_345L, "USD").toMajorUnits()).isEqualByComparingTo(new BigDecimal("123.45"));
        // Yen has no minor unit, so the same 12345 is twelve thousand yen, not a hundred.
        assertThat(Money.of(12_345L, "JPY").toMajorUnits()).isEqualByComparingTo(new BigDecimal("12345"));
        assertThat(Money.of(12_345L, "BHD").toMajorUnits()).isEqualByComparingTo(new BigDecimal("12.345"));
    }

    @Test
    @DisplayName("refuses to combine different currencies")
    void refusesCurrencyMixing() {
        assertThatThrownBy(() -> Money.of(100L, "USD").plus(Money.of(100L, "EUR")))
                .isInstanceOf(Money.CurrencyMismatchException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");
    }

    @Test
    @DisplayName("rejects anything that is not an ISO-4217 code")
    void rejectsBadCurrencyCode() {
        assertThatThrownBy(() -> Money.of(1L, "usd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1L, "DOLLARS")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(1L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("arithmetic is exact and overflow throws rather than wrapping")
    void arithmeticIsExact() {
        assertThat(Money.of(1999L, "USD").plus(Money.of(1L, "USD")).minorUnits()).isEqualTo(2000L);
        assertThat(Money.of(-500L, "USD").abs().minorUnits()).isEqualTo(500L);
        assertThat(Money.of(100L, "USD").absoluteDifference(Money.of(130L, "USD"))).isEqualTo(30L);

        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1L, "USD")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("falls back to two digits for a currency the JVM does not know")
    void unknownCurrencyFallsBack() {
        assertThat(Money.fractionDigits("XYZ")).isEqualTo(2);
    }
}
