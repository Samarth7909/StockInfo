package com.indira.opsconsole.ingest.adapter;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Parses the DP position extract HTML table (Jsoup).
 * Expected table columns (order-independent, case-insensitive):
 *   CLIENT_ID, ISIN, POSITION_TYPE, EXCHANGE_SYMBOL, SETTLED_QTY, MOVEMENT_STATE, PENDING_QTY, CUT_AT
 *
 * Domain rules enforced here:
 * - Rows with MOVEMENT_STATE=PENDING are emitted with marker "row_type=PENDING".
 *   The IngestService persists these to dp_pending_movements, never to normalized_holdings.
 * - Rows with MOVEMENT_STATE=SETTLED (or absent) go to normalized_holdings with data_source=DP.
 * - A pending quantity must NOT be added to settled quantity.
 */
@Slf4j
@Component
public class DPHtmlAdapter implements StreamAdapter {

    @Override
    public String streamName() {
        return "DP_EXTRACT";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        List<Map<String, String>> rows  = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        try {
            Document doc = Jsoup.parse(new String(raw, StandardCharsets.UTF_8));
            Element table = doc.select("table").first();
            if (table == null) {
                errors.add(ParseResult.RowError.builder()
                    .rowIndex(-1).field("file")
                    .message("No <table> element found in HTML").build());
                return ParseResult.builder()
                    .rawRows(rows).errors(errors).detectedCut(null).schemaVersion("v1").build();
            }

            // Build header index from <th> or first <tr>
            Elements headers = table.select("thead tr th");
            if (headers.isEmpty()) {
                headers = table.select("tr:first-child td, tr:first-child th");
            }
            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                colIndex.put(NormalizationUtil.canonicalHeader(headers.get(i).text()), i);
            }

            Elements dataRows = table.select("tbody tr");
            if (dataRows.isEmpty()) {
                // No tbody — skip first row (header)
                Elements allRows = table.select("tr");
                dataRows = new Elements(allRows.subList(1, allRows.size()));
            }

            int rowIdx = 0;
            for (Element tr : dataRows) {
                Elements tds = tr.select("td");
                String[] cols = tds.stream().map(Element::text).toArray(String[]::new);
                rowIdx++;

                String clientId   = getCol(cols, colIndex, "client_id");
                String isin       = getCol(cols, colIndex, "isin");
                String posType    = getCol(cols, colIndex, "position_type");
                String symbol     = getCol(cols, colIndex, "exchange_symbol");
                String settledStr = getCol(cols, colIndex, "settled_qty");
                String movState   = getCol(cols, colIndex, "movement_state");
                String pendingStr = getCol(cols, colIndex, "pending_qty");
                String cutStr     = getCol(cols, colIndex, "cut_at");

                String normClientId = NormalizationUtil.normalizeClientId(clientId);
                String normIsin     = NormalizationUtil.normalizeIsin(isin);
                Instant cut         = NormalizationUtil.parseInstant(cutStr);

                boolean hasError = false;
                if (normClientId == null) { errors.add(rowError(rowIdx, "client_id", "missing", clientId)); hasError = true; }
                if (normIsin == null)     { errors.add(rowError(rowIdx, "isin", "missing", isin)); hasError = true; }
                if (cut == null)          { errors.add(rowError(rowIdx, "cut_at", "unparseable", cutStr)); hasError = true; }
                if (hasError) continue;

                String stateUpper = (movState != null) ? movState.strip().toUpperCase() : "SETTLED";
                boolean isPending = "PENDING".equals(stateUpper);

                Long settledQty = NormalizationUtil.parseQty(settledStr);
                Long pendingQty = NormalizationUtil.parseQty(pendingStr);

                if (!isPending && settledQty == null) {
                    errors.add(rowError(rowIdx, "settled_qty", "not a valid integer", settledStr));
                    continue;
                }
                if (isPending && pendingQty == null) {
                    errors.add(rowError(rowIdx, "pending_qty", "not a valid integer", pendingStr));
                    continue;
                }

                Map<String, String> row = new LinkedHashMap<>();
                row.put("client_id",      normClientId);
                row.put("isin",           normIsin);
                row.put("position_type",  posType != null ? posType.strip().toUpperCase() : "EQUITY");
                row.put("exchange_symbol", symbol != null ? symbol.strip().toUpperCase() : null);
                row.put("settled_qty",    settledQty != null ? String.valueOf(settledQty) : "0");
                row.put("pending_qty",    pendingQty != null ? String.valueOf(pendingQty) : "0");
                row.put("movement_state", stateUpper);
                row.put("cut_at",         cut.toString());
                // row_type tells the IngestService which table to persist to
                row.put("row_type",       isPending ? "PENDING" : "SETTLED");

                rows.add(row);

                if (detectedCut == null || cut.isAfter(detectedCut)) detectedCut = cut;
            }

        } catch (Exception e) {
            log.error("DPHtmlAdapter failed to parse {}: {}", filename, e.getMessage());
            errors.add(ParseResult.RowError.builder()
                .rowIndex(-1).field("file").message("Parse failure: " + e.getMessage()).build());
        }

        return ParseResult.builder()
            .rawRows(rows).errors(errors).detectedCut(detectedCut).schemaVersion("v1").build();
    }

    private String getCol(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) return null;
        String v = cols[i];
        return (v == null || v.isBlank()) ? null : v.strip();
    }

    private ParseResult.RowError rowError(int ri, String f, String m, String rv) {
        return ParseResult.RowError.builder().rowIndex(ri).field(f).message(m).rawValue(rv).build();
    }
}
