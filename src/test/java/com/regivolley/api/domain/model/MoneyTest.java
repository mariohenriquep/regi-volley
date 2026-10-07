package com.regivolley.api.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @ParameterizedTest
    @CsvSource({"12.50, 1250", "12.5, 1250", "12, 1200", "0, 0", "0.01, 1"})
    void convertsEurosToExactCents(String euros, long cents) {
        // Arrange
        BigDecimal amount = new BigDecimal(euros);

        // Act
        Money money = Money.ofEuros(amount);

        // Assert
        assertThat(money.cents()).isEqualTo(cents);
        assertThat(money).isEqualTo(Money.ofCents(cents));
    }

    @Test
    void exposesEurosWithTwoDecimalsAndPrintsTheCurrency() {
        // Arrange
        Money money = Money.ofCents(1205);

        // Act
        BigDecimal euros = money.euros();

        // Assert
        assertThat(euros).isEqualByComparingTo("12.05");
        assertThat(euros.scale()).isEqualTo(2);
        assertThat(money.toString()).isEqualTo("12.05 EUR");
    }

    @Test
    void doesNotLoseCentsToBinaryFloatingPoint() {
        // Arrange
        Money tenCents = Money.ofEuros(new BigDecimal("0.10"));

        // Act
        long total = tenCents.cents() * 3;

        // Assert
        assertThat(total).isEqualTo(30);
    }

    @Test
    void rejectsNegativeAmounts() {
        // Arrange
        Executable act = () -> Money.ofCents(-1);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("negative");
    }

    @Test
    void rejectsMoreThanTwoDecimalsInsteadOfRounding() {
        // Arrange
        Executable act = () -> Money.ofEuros(new BigDecimal("1.005"));

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("two decimals");
    }

    @Test
    void rejectsANullAmount() {
        // Arrange
        Executable act = () -> Money.ofEuros(null);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("euros");
    }
}
