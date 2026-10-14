package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InnTest {
    @Test
    void validatesTenAndTwelveDigitNumbers() {
        assertThat(Inn.isValid("7701000019")).isTrue();
        assertThat(Inn.isValid("500100000199")).isTrue();
        assertThat(Inn.isValid("7701000018")).isFalse();
        assertThat(Inn.isValid("500100000198")).isFalse();
        assertThat(Inn.isValid("123")).isFalse();
        assertThat(Inn.isValid(null)).isFalse();
    }

    @Test
    void completesCheckDigits() {
        assertThat(Inn.complete("770100001")).isEqualTo("7701000019");
        assertThat(Inn.complete("5001000001")).isEqualTo("500100000199");
    }
}
