package com.indira.opsconsole.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * JPA AttributeConverter that maps Java {@link Instant} to a SQLite TEXT column
 * and back, handling both ISO-8601 ({@code 2024-09-16T18:00:00Z}) and SQLite's
 * native {@code datetime('now')} format ({@code 2024-09-16 18:00:00}).
 *
 * <p>This avoids the "Error parsing time stamp" exception thrown by Hibernate's
 * default mapping when it encounters space-separated datetime strings stored by
 * SQLite's built-in {@code datetime()} function.</p>
 */
@Converter(autoApply = true)
public class SqliteInstantConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter ISO_COMPACT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter SQLITE_SPACE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @Override
    public String convertToDatabaseColumn(Instant attribute) {
        if (attribute == null) return null;
        return ISO_COMPACT.format(attribute);
    }

    @Override
    public Instant convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        String s = dbData.strip();
        // Try standard ISO with 'Z' suffix
        try { return Instant.parse(s.endsWith("Z") ? s : s + "Z"); }
        catch (DateTimeParseException ignored) {}
        // Try ISO without fractional seconds
        try { return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .toInstant(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) {}
        // Try SQLite space-separated format
        try { return LocalDateTime.parse(s, SQLITE_SPACE).toInstant(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) {}
        // Try date-only
        try { return java.time.LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC); }
        catch (DateTimeParseException ignored) {}
        throw new IllegalArgumentException("Cannot parse timestamp from SQLite: [" + s + "]");
    }
}
