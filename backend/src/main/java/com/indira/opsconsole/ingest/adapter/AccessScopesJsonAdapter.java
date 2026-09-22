package com.indira.opsconsole.ingest.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the ownership and demo-user access manifest as immutable raw evidence.
 * Credentials are intentionally not persisted or returned by this adapter.
 */
@Component
@RequiredArgsConstructor
public class AccessScopesJsonAdapter implements StreamAdapter {

    private final ObjectMapper objectMapper;

    @Override
    public String streamName() {
        return "ACCESS_SCOPES";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(new String(raw, StandardCharsets.UTF_8));
            addArrayRows(root, "clients", "CLIENT", rows);
            addArrayRows(root, "demo_users", "DEMO_USER", rows);
            Instant cut = root.path("generated_at").isTextual()
                ? NormalizationUtil.parseInstant(root.path("generated_at").asText())
                : null;
            return ParseResult.builder()
                .rawRows(rows)
                .errors(errors)
                .detectedCut(cut)
                .schemaVersion("v1")
                .build();
        } catch (Exception e) {
            errors.add(ParseResult.RowError.builder()
                .rowIndex(-1)
                .field("file")
                .message("JSON parse failure: " + e.getMessage())
                .rawValue(null)
                .build());
            return ParseResult.builder().rawRows(rows).errors(errors).detectedCut(null).schemaVersion("v1").build();
        }
    }

    private void addArrayRows(JsonNode root, String field, String type, List<Map<String, String>> rows) {
        JsonNode array = root.path(field);
        if (!array.isArray()) return;
        for (JsonNode item : array) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("record_type", type);
            item.fields().forEachRemaining(entry -> {
                if ("password".equals(entry.getKey())) return;
                JsonNode value = entry.getValue();
                row.put(entry.getKey(), value.isArray() ? value.toString() : value.asText());
            });
            rows.add(row);
        }
    }
}
