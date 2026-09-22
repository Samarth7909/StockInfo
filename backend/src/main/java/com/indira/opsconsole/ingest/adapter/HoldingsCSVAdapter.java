package com.indira.opsconsole.ingest.adapter;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Parses the internal holdings snapshot CSV.
 * Expected columns (order-independent, case-insensitive):
 *   client_id, isin, position_type, exchange_symbol, series, settled_qty, cut_at
 *
 * Domain rule: holding identity = (client_id, ISIN, position_type, cut_at).
 * Exchange symbol and series are metadata, never join keys.
 */
@Slf4j
@Component
public class HoldingsCSVAdapter implements StreamAdapter {

    @Override
    public String streamName() {
        return "HOLDINGS";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8))) {

            List<String[]> all = reader.readAll();
            if (all.isEmpty()) {
                return ParseResult.builder()
                    .rawRows(rows).errors(errors).detectedCut(null).schemaVersion("v1").build();
            }

            // Build canonical header map (position → canonical name)
            String[] headerRow = all.get(0);
            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.length; i++) {
                colIndex.put(NormalizationUtil.canonicalHeader(headerRow[i]), i);
            }

            for (int r = 1; r < all.size(); r++) {
                String[] cols = all.get(r);
                Map<String, String> row = new LinkedHashMap<>();

                String clientId = getCol(cols, colIndex, "client_id");
                String isin     = getCol(cols, colIndex, "isin");
                String posType  = getCol(cols, colIndex, "position_type");
                String symbol   = getCol(cols, colIndex, "exchange_symbol");
                String series   = getCol(cols, colIndex, "series");
                String qtyStr   = getCol(cols, colIndex, "settled_qty");
                String cutStr   = getCol(cols, colIndex, "cut_at");

                // Normalize
                String normClientId = NormalizationUtil.normalizeClientId(clientId);
                String normIsin     = NormalizationUtil.normalizeIsin(isin);
                String normPosType  = NormalizationUtil.normalizeString(posType);
                Long   qty          = NormalizationUtil.parseQty(qtyStr);
                Instant cut         = NormalizationUtil.parseInstant(cutStr);

                boolean hasError = false;
                if (normClientId == null) {
                    errors.add(rowError(r, "client_id", "missing or blank", clientId));
                    hasError = true;
                }
                if (normIsin == null) {
                    errors.add(rowError(r, "isin", "missing or blank", isin));
                    hasError = true;
                }
                if (qty == null) {
                    errors.add(rowError(r, "settled_qty", "not a valid integer", qtyStr));
                    hasError = true;
                }
                if (cut == null) {
                    errors.add(rowError(r, "cut_at", "unparseable timestamp", cutStr));
                    hasError = true;
                }

                if (hasError) continue;  // quarantine row, do not add to output

                row.put("client_id",      normClientId);
                row.put("isin",           normIsin);
                row.put("position_type",  normPosType != null ? normPosType.toUpperCase() : "EQUITY");
                row.put("exchange_symbol", symbol != null ? symbol.strip().toUpperCase() : null);
                row.put("series",         series != null ? series.strip().toUpperCase() : null);
                row.put("settled_qty",    String.valueOf(qty));
                row.put("cut_at",         cut.toString());

                rows.add(row);

                if (detectedCut == null || cut.isAfter(detectedCut)) {
                    detectedCut = cut;
                }
            }
        } catch (Exception e) {
            log.error("HoldingsCSVAdapter failed to parse {}: {}", filename, e.getMessage());
            errors.add(ParseResult.RowError.builder()
                .rowIndex(-1).field("file").message("Parse failure: " + e.getMessage()).build());
        }

        return ParseResult.builder()
            .rawRows(rows).errors(errors)
            .detectedCut(detectedCut).schemaVersion("v1")
            .build();
    }

    private String getCol(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) return null;
        String v = cols[i];
        return (v == null || v.isBlank()) ? null : v.strip();
    }

    private ParseResult.RowError rowError(int rowIndex, String field, String msg, String raw) {
        return ParseResult.RowError.builder()
            .rowIndex(rowIndex).field(field).message(msg).rawValue(raw).build();
    }
}
