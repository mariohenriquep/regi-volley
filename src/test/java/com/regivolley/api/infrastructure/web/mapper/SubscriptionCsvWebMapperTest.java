package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.infrastructure.web.dto.PaymentStatusName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** US-22: a cell is data, never a formula (OWASP CSV injection), and the file is plain RFC 4180. */
class SubscriptionCsvWebMapperTest {

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\t=1", "\r=1"})
    void aCellThatStartsLikeAFormulaGetsALeadingApostrophe(String text) {
        // Arrange
        // (the text)

        // Act
        String cell = SubscriptionCsvWebMapper.cell(text);

        // Assert
        assertThat(cell.replace("\"", "")).startsWith("'" + text.charAt(0));
    }

    static Stream<Arguments> hiddenFormulas() {
        return Stream.of(
                Arguments.of("a space", " =1+1"), Arguments.of("two spaces", "  +1"), Arguments.of("a no-break space", "\u00A0=cmd"),
                Arguments.of("a zero-width space", "\u200B@SUM(A1)"), Arguments.of("a byte order mark", "\uFEFF-2"),
                Arguments.of("a newline", "\n=1"), Arguments.of("a vertical tab", "\u000B=1"), Arguments.of("a form feed", "\u000C+1"),
                Arguments.of("several mixed", " \u200B\u00A0\uFEFF=1"), Arguments.of("a tab behind a space", " \t=1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hiddenFormulas")
    void aFormulaHiddenBehindInvisibleOrBlankCharactersIsDefusedToo(String why, String text) {
        // Arrange
        // (the text)

        // Act
        String cell = SubscriptionCsvWebMapper.cell(text);

        // Assert
        assertThat(cell.replace("\"", "")).as(why).startsWith("'" + text.charAt(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {" Ana Silva", "\u00A0Ana", "\u200BAna", "Ana =1", "a+b", "   ", "\u200B", "1=1", "(=1)"})
    void textWhoseFirstRealCharacterIsNotAFormulaStarterIsLeftAlone(String text) {
        // Arrange
        // (the text)

        // Act
        String cell = SubscriptionCsvWebMapper.cell(text);

        // Assert
        assertThat(cell).isEqualTo(text);
    }

    @Test
    void aCellWithACommaAQuoteOrALineBreakIsQuotedAndItsQuotesDoubled() {
        // Arrange
        String comma = "Silva, Ana";
        String quote = "Ana \"Nana\" Silva";
        String newline = "Ana\nSilva";

        // Act
        String first = SubscriptionCsvWebMapper.cell(comma);
        String second = SubscriptionCsvWebMapper.cell(quote);
        String third = SubscriptionCsvWebMapper.cell(newline);

        // Assert
        assertThat(first).isEqualTo("\"Silva, Ana\"");
        assertThat(second).isEqualTo("\"Ana \"\"Nana\"\" Silva\"");
        assertThat(third).isEqualTo("\"Ana\nSilva\"");
    }

    @Test
    void anOrdinaryCellAnEmptyCellAndANullAreLeftAlone() {
        // Arrange
        // (no input)

        // Act
        String plain = SubscriptionCsvWebMapper.cell("Ana Silva");
        String empty = SubscriptionCsvWebMapper.cell("");
        String none = SubscriptionCsvWebMapper.cell(null);
        String date = SubscriptionCsvWebMapper.cell("2026-10-12");

        // Assert
        assertThat(plain).isEqualTo("Ana Silva");
        assertThat(empty).isEmpty();
        assertThat(none).isEmpty();
        assertThat(date).isEqualTo("2026-10-12");
    }

    @Test
    void anEmptyListIsJustTheHeaderAfterTheByteOrderMark() {
        // Arrange
        // (no entries)

        // Act
        String csv = new String(SubscriptionCsvWebMapper.toCsv(List.of()), StandardCharsets.UTF_8);

        // Assert
        assertThat(csv).isEqualTo("﻿" + SubscriptionCsvWebMapper.HEADER + "\r\n");
    }

    @Test
    void theFileNameNamesTheStatusInLowerCase() {
        // Arrange
        // (the statuses)

        // Act
        String overdue = SubscriptionCsvWebMapper.fileName(PaymentStatusName.OVERDUE);
        String pending = SubscriptionCsvWebMapper.fileName(PaymentStatusName.PENDING);

        // Assert
        assertThat(overdue).isEqualTo("subscriptions-overdue.csv");
        assertThat(pending).isEqualTo("subscriptions-pending.csv");
    }
}
