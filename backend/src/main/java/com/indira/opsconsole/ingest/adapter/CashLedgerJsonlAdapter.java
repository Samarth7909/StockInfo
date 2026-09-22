package com.indira.opsconsole.ingest.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Parses JSON Lines (one JSON object per line) cash ledger.
 * Expected fields per line:
 *   event_id, client_id, amount_paise, state (POSTED|PENDING), effective_at, narration
 *
 * Domain rules enforced:
 * - amount_paise is a signed integer — never parsed as float.
 * - PENDING events are preserved but excluded from posted balance computation.
 * - A later effective_at is not retroactively posted at an earlier cut.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CashLedgerJsonlAdapter implements StreamAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public String streamName() {
        return "CASH_LEDGER";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        List<Map<String, String>> rows  = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        String content = new String(raw, StandardCharsets.UTF_8);
        String[] lines = content.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) continue;

            try {
                Map<String, Object> json = objectMapper.readValue(
                    line, new TypeReference<Map<String, Object>>() {});

                String clientId   = getString(json, "client_id");
                String eventId    = getString(json, "event_id");
                Object amtRaw     = json.get("amount_paise");
                String state      = getString(json, "state");
                String effectiveAt = getString(json, "effective_at");
                String narration  = getString(json, "narration");

                String normClientId = NormalizationUtil.normalizeClientId(clientId);
                Instant effective   = NormalizationUtil.parseInstant(effectiveAt);

                boolean hasError = false;
                if (normClientId == null) { errors.add(rowError(i, "client_id", "missing", clientId)); hasError = true; }
                if (amtRaw == null)       { errors.add(rowError(i, "amount_paise", "missing", null)); hasError = true; }
                if (effective == null)    { errors.add(rowError(i, "effective_at", "unparseable", effectiveAt)); hasError = true; }
                if (!"POSTED".equalsIgnoreCase(state) && !"PENDING".equalsIgnoreCase(state)) {
                    errors.add(rowError(i, "state", "must be POSTED or PENDING", state));
                    hasError = true;
                }
                if (hasError) continue;

                // Parse amount_paise as exact integer — reject if it arrives as a float
                Long amountPaise;
                if (amtRaw instanceof Integer) {
                    amountPaise = ((Integer) amtRaw).longValue();
                } else if (amtRaw instanceof Long) {
                    amountPaise = (Long) amtRaw;
                } else if (amtRaw instanceof String) {
                    amountPaise = NormalizationUtil.parseAmountPaise((String) amtRaw, true);
                } else if (amtRaw instanceof Double) {
                    // Reject: floating-point amount in a paise field is ambiguous
                    errors.add(rowError(i, "amount_paise",
                        "floating-point value rejected; paise must be integer", amtRaw.toString()));
                    continue;
                } else {
                    errors.add(rowError(i, "amount_paise", "unrecognised type: " + amtRaw.getClass(), amtRaw.toString()));
                    continue;
                }

                if (amountPaise == null) {
                    errors.add(rowError(i, "amount_paise", "not a valid integer", amtRaw.toString()));
                    continue;
                }

                Map<String, String> row = new LinkedHashMap<>();
                row.put("event_id",    eventId);
                row.put("client_id",   normClientId);
                row.put("amount_paise", String.valueOf(amountPaise));
                row.put("state",       state.toUpperCase());
                row.put("effective_at", effective.toString());
                row.put("narration",   narration);

                rows.add(row);

                if ("POSTED".equalsIgnoreCase(state)) {
                    if (detectedCut == null || effective.isAfter(detectedCut)) detectedCut = effective;
                }

            } catch (Exception e) {
                log.warn("CashLedgerJsonlAdapter: error on line {}: {}", i, e.getMessage());
                errors.add(rowError(i, "line", "JSON parse error: " + e.getMessage(), line));
            }
        }

        return ParseResult.builder()
            .rawRows(rows).errors(errors).detectedCut(detectedCut).schemaVersion("v1").build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private ParseResult.RowError rowError(int ri, String f, String m, String rv) {
        return ParseResult.RowError.builder().rowIndex(ri).field(f).message(m).rawValue(rv).build();
    }
}
