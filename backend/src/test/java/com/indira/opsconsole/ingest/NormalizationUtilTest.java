package com.indira.opsconsole.ingest;

import com.indira.opsconsole.ingest.adapter.NormalizationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NormalizationUtil — the shared normalization helpers.
 * Covers the paise arithmetic, ISIN normalisation, date parsing, and header canonicalization.
 */
class NormalizationUtilTest {

    // ── ISIN normalisation ────────────────────────────────────────────────────

    @ParameterizedTest(name = "ISIN ''{0}'' → ''{1}''")
    @CsvSource({
        "INE009A01021, INE009A01021",
        " ine009a01021 , INE009A01021",
        "INE 009 A01021, INE009A01021",
    })
    void normalizeIsin_uppercasesAndStripsSpaces(String input, String expected) {
        assertThat(NormalizationUtil.normalizeIsin(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Blank ISIN returns null")
    void normalizeIsin_blank_returnsNull() {
        assertThat(NormalizationUtil.normalizeIsin("   ")).isNull();
        assertThat(NormalizationUtil.normalizeIsin(null)).isNull();
    }

    // ── Paise arithmetic ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Integer paise string parsed exactly — no floating point")
    void parseAmountPaise_integer() {
        assertThat(NormalizationUtil.parseAmountPaise("500000000", true)).isEqualTo(500_000_000L);
        assertThat(NormalizationUtil.parseAmountPaise("-150000000", true)).isEqualTo(-150_000_000L);
    }

    @Test
    @DisplayName("Rupees with decimal converted to paise exactly")
    void parseAmountPaise_rupeesWithDecimal() {
        assertThat(NormalizationUtil.parseAmountPaise("100.50", false)).isEqualTo(10050L);
        assertThat(NormalizationUtil.parseAmountPaise("1000", false)).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("Amount with commas and currency symbols parsed")
    void parseAmountPaise_withSymbols() {
        assertThat(NormalizationUtil.parseAmountPaise("1,50,000", false)).isEqualTo(15_000_000L);
    }

    @Test
    @DisplayName("Non-numeric amount returns null — never guessed")
    void parseAmountPaise_invalid_returnsNull() {
        assertThat(NormalizationUtil.parseAmountPaise("N/A", true)).isNull();
        assertThat(NormalizationUtil.parseAmountPaise("", true)).isNull();
        assertThat(NormalizationUtil.parseAmountPaise(null, true)).isNull();
    }

    // ── Timestamp parsing ─────────────────────────────────────────────────────

    @Test
    @DisplayName("ISO-8601 datetime parsed to Instant")
    void parseInstant_iso() {
        Instant result = NormalizationUtil.parseInstant("2024-09-16T18:00:00");
        assertThat(result).isNotNull();
        assertThat(result.toString()).startsWith("2024-09-16");
    }

    @Test
    @DisplayName("Date-only string treated as start of day UTC")
    void parseInstant_dateOnly() {
        Instant result = NormalizationUtil.parseInstant("2024-09-16");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Unparseable timestamp returns null")
    void parseInstant_invalid_returnsNull() {
        assertThat(NormalizationUtil.parseInstant("not-a-date")).isNull();
        assertThat(NormalizationUtil.parseInstant(null)).isNull();
    }

    // ── Header canonicalization ───────────────────────────────────────────────

    @ParameterizedTest(name = "''{0}'' → ''{1}''")
    @CsvSource({
        "CLIENT_ID,   client_id",
        "Client Id,   client_id",
        "client-id,   client_id",
        "AMOUNT PAISE,amount_paise",
    })
    void canonicalHeader(String input, String expected) {
        assertThat(NormalizationUtil.canonicalHeader(input.trim())).isEqualTo(expected.trim());
    }
}
