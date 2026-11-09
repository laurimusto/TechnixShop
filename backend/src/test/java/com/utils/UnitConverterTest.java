package com.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class UnitConverterTest {

    @Test
    @DisplayName("kgToLbs: converts kilograms to pounds, rounded to 2 decimal places")
    void kgToLbs_convertsAndRounds() {
        BigDecimal result = UnitConverter.kgToLbs(new BigDecimal("2.496"));

        assertThat(result).isEqualByComparingTo("5.50");
    }

    @Test
    @DisplayName("kgToLbs: null input returns null instead of throwing")
    void kgToLbs_nullInputReturnsNull() {
        assertThat(UnitConverter.kgToLbs(null)).isNull();
    }

    @Test
    @DisplayName("cmToInch: converts centimetres to inches, rounded to 2 decimal places")
    void cmToInch_convertsAndRounds() {
        BigDecimal result = UnitConverter.cmToInch(new BigDecimal("34.79"));

        assertThat(result).isEqualByComparingTo("13.70");
    }

    @Test
    @DisplayName("cmToInch: handles a value whose exact division is non-terminating without throwing")
    void cmToInch_nonTerminatingDivisionDoesNotThrow() {
        BigDecimal result = UnitConverter.cmToInch(new BigDecimal("1"));

        assertThat(result).isEqualByComparingTo("0.39");
    }

    @Test
    @DisplayName("cmToInch: null input returns null instead of throwing")
    void cmToInch_nullInputReturnsNull() {
        assertThat(UnitConverter.cmToInch(null)).isNull();
    }
}
