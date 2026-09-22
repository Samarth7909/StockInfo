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
 * Parses the exchange reference CSV (symbol/ISIN/series mapping and reference prices).
 * Expected columns (order-independent, case-insensitive):
 *   isin, symbol, series, exchange, reference_price_paise, effective_date
 *
 * Domain rules:
 * - ISIN is the only cross-stream security key. Symbol is display metadata.
 * - reference_price_paise is a reference, NOT evidence of account entitlement.
 * - exchange column maps NSE vs BSE — same ISIN can appear on both with different symbols.
 */
@Slf4j
@Component
public class ExchangeCSVAdapter implements StreamAdapter {

    @Override
    public String streamName() {
        return "EXCHANGE_REF";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        List<Map<String, String>> rows  = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(new ByteArrayInputStream(raw), StandardCharsets.UTF_8))) {

            List<String[]> all = reader.readAll();
            if (all.isEmpty()) return emptyResult();

            String[] headerRow = all.get(0);
            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.length; i++) {
                colIndex.put(NormalizationUtil.canonicalHeader(headerRow[i]), i);
            }

            for (int r = 1; r < all.size(); r++) {
                String[] cols = all.get(r);

                String isin         = getCol(cols, colIndex, "isin");
                String symbol       = getCol(cols, colIndex, "symbol");
                String series       = getCol(cols, colIndex, "series");
                String exchange     = getCol(cols, colIndex, "exchange");
                String priceStr     = getCol(cols, colIndex, "reference_price_paise");
                String effectiveStr = getCol(cols, colIndex, "effective_date");

                String normIsin = NormalizationUtil.normalizeIsin(isin);
                if (normIsin == null) {
                    errors.add(rowError(r, "isin", "missing or blank", isin));
                    continue;
                }

                Long pricePaise = NormalizationUtil.parseAmountPaise(priceStr, true);
                // price is optional — missing price is not an error, just unknown
                Instant cut = NormalizationUtil.parseInstant(effectiveStr);

                Map<String, String> row = new LinkedHashMap<>();
                row.put("isin",                   normIsin);
                row.put("symbol",                 symbol != null ? symbol.strip().toUpperCase() : null);
                row.put("series",                 series != null ? series.strip().toUpperCase() : null);
                row.put("exchange",               exchange != null ? exchange.strip().toUpperCase() : null);
                row.put("reference_price_paise",  pricePaise != null ? String.valueOf(pricePaise) : null);
                row.put("effective_date",         effectiveStr);

                rows.add(row);

                if (cut != null && (detectedCut == null || cut.isAfter(detectedCut))) detectedCut = cut;
            }

        } catch (Exception e) {
            log.error("ExchangeCSVAdapter failed to parse {}: {}", filename, e.getMessage());
            errors.add(rowError(-1, "file", "Parse failure: " + e.getMessage(), null));
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

    private ParseResult emptyResult() {
        return ParseResult.builder().rawRows(List.of()).errors(List.of()).detectedCut(null).schemaVersion("v1").build();
    }

    private ParseResult.RowError rowError(int ri, String f, String m, String rv) {
        return ParseResult.RowError.builder().rowIndex(ri).field(f).message(m).rawValue(rv).build();
    }
}
