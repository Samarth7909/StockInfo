package com.indira.opsconsole.ingest.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Shared normalization helpers used by all adapters.
 * All string operations are null-safe and never throw on malformed input;
 * they return null so callers can quarantine the row explicitly.
 */
public final class NormalizationUtil {

    private NormalizationUtil() {}

    private static final List<DateTimeFormatter> DATETIME_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
    );

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MMddyyyy"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    /** Trim, collapse internal whitespace, and lowercase. Returns null for blank/null input. */
    public static String normalizeString(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty()) return null;
        return trimmed.toLowerCase().replaceAll("\\s+", " ");
    }

    /** Normalize ISIN: uppercase, strip spaces. */
    public static String normalizeIsin(String value) {
        if (value == null) return null;
        String trimmed = value.strip().toUpperCase().replaceAll("\\s+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Normalize client ID: uppercase, strip spaces. */
    public static String normalizeClientId(String value) {
        if (value == null) return null;
        String trimmed = value.strip().toUpperCase().replaceAll("\\s+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Parse a timestamp string with multiple format attempts. Returns null if unparseable. */
    public static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.strip();
        for (DateTimeFormatter fmt : DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(v, fmt).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {}
        }
        // Try date-only — treat as start of day UTC
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, fmt).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /** Parse a date string. Returns null if unparseable. */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.strip();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(v, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    /**
     * Parse an amount that may be in rupees (decimal) or paise (integer).
     * We NEVER guess the unit from magnitude — callers must declare the unit.
     * Returns the value unchanged as a long integer.
     *
     * @param value     raw string value
     * @param unitPaise true if the raw value is already in paise; false if it's in rupees (multiply by 100)
     */
    public static Long parseAmountPaise(String value, boolean unitPaise) {
        if (value == null || value.isBlank()) return null;
        // Remove commas, currency symbols, and spaces
        String cleaned = value.strip()
            .replaceAll(",", "")
            .replaceAll("[₹$]", "")
            .replaceFirst("(?i)^rs\\.?\\s*", "")
            .strip();
        try {
            if (unitPaise) {
                return Long.parseLong(cleaned);
            } else {
                // Rupees → paise (exact, using BigDecimal-like integer arithmetic)
                if (cleaned.contains(".")) {
                    String[] parts = cleaned.split("\\.");
                    long rupees = Long.parseLong(parts[0]);
                    String paiseStr = (parts.length > 1 ? parts[1] : "0") + "00";
                    long paise = Long.parseLong(paiseStr.substring(0, 2));
                    return rupees * 100L + (rupees < 0 ? -paise : paise);
                } else {
                    return Long.parseLong(cleaned) * 100L;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parse a quantity string (integer). Returns null if unparseable. */
    public static Long parseQty(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.strip().replaceAll(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Map common column-name variants to a canonical name (case-insensitive, space/underscore-agnostic). */
    public static String canonicalHeader(String raw) {
        if (raw == null) return "";
        return raw.strip().toLowerCase()
            .replaceAll("[\\s_\\-]+", "_");
    }
}
