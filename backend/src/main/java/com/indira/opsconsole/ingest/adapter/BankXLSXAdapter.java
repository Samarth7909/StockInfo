package com.indira.opsconsole.ingest.adapter;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.*;

/**
 * Parses the bank confirmation XLSX file (Apache POI).
 * Expected columns (order-independent, case-insensitive):
 *   CLIENT_ID, UTR_REFERENCE, DIRECTION, AMOUNT_PAISE, BANK_STATUS, VALUE_DATE, NARRATION
 *
 * Domain rules:
 * - A bank entry can support a cash case ONLY after human/documented verification.
 *   humanMatched defaults to false; we never auto-match.
 * - Duplicate UTR references are preserved (not de-duplicated) — they are CONFLICTING_EVIDENCE.
 * - amount_paise is always positive here; direction (CREDIT/DEBIT) carries the sign.
 *
 * Falls back to CSV if the file extension is .csv (for testing without actual XLSX).
 */
@Slf4j
@Component
public class BankXLSXAdapter implements StreamAdapter {

    private final DataFormatter dataFormatter = new DataFormatter();

    @Override
    public String streamName() {
        return "BANK_CONFIRMATION";
    }

    @Override
    public ParseResult parse(byte[] raw, String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".csv")) {
            return parseCsv(raw, filename);
        }
        return parseXlsx(raw, filename);
    }

    private ParseResult parseXlsx(byte[] raw, String filename) {
        List<Map<String, String>> rows  = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(raw))) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(rowError(-1, "file", "Empty sheet or missing header row", null));
                return ParseResult.builder().rawRows(rows).errors(errors).detectedCut(null).schemaVersion("v1").build();
            }

            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (Cell cell : headerRow) {
                colIndex.put(NormalizationUtil.canonicalHeader(cellStr(cell)), cell.getColumnIndex());
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String clientId  = getXlsxCol(row, colIndex, "client_id");
                String utr       = getXlsxCol(row, colIndex, "utr_reference");
                String direction = getXlsxCol(row, colIndex, "direction");
                String amtStr    = getXlsxCol(row, colIndex, "amount_paise");
                String status    = getXlsxCol(row, colIndex, "bank_status");
                String valueDate = getXlsxCol(row, colIndex, "value_date");
                String narration = getXlsxCol(row, colIndex, "narration");

                Long amountPaise = NormalizationUtil.parseAmountPaise(amtStr, true);
                if (amountPaise == null) {
                    errors.add(rowError(r, "amount_paise", "not a valid integer", amtStr));
                    continue;
                }
                if (direction != null && !direction.equalsIgnoreCase("CREDIT") && !direction.equalsIgnoreCase("DEBIT")) {
                    errors.add(rowError(r, "direction", "must be CREDIT or DEBIT", direction));
                    continue;
                }

                Map<String, String> parsedRow = buildRow(clientId, utr, direction, amountPaise, status, valueDate, narration);
                rows.add(parsedRow);

                Instant vd = NormalizationUtil.parseInstant(valueDate);
                if (vd != null && (detectedCut == null || vd.isAfter(detectedCut))) detectedCut = vd;
            }
        } catch (Exception e) {
            log.error("BankXLSXAdapter failed to parse XLSX {}: {}", filename, e.getMessage());
            errors.add(rowError(-1, "file", "XLSX parse failure: " + e.getMessage(), null));
        }

        return ParseResult.builder().rawRows(rows).errors(errors).detectedCut(detectedCut).schemaVersion("v1").build();
    }

    /** CSV fallback for tests and dev environments without XLSX generation. */
    private ParseResult parseCsv(byte[] raw, String filename) {
        List<Map<String, String>> rows  = new ArrayList<>();
        List<ParseResult.RowError> errors = new ArrayList<>();
        Instant detectedCut = null;

        try {
            String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\\r?\\n");
            if (lines.length == 0) return emptyResult();

            // Parse header
            String[] headers = lines[0].split(",");
            Map<String, Integer> colIndex = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(NormalizationUtil.canonicalHeader(headers[i]), i);
            }

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty()) continue;
                String[] cols = line.split(",", -1);

                String clientId  = getCol(cols, colIndex, "client_id");
                String utr       = getCol(cols, colIndex, "utr_reference");
                String direction = getCol(cols, colIndex, "direction");
                String amtStr    = getCol(cols, colIndex, "amount_paise");
                String status    = getCol(cols, colIndex, "bank_status");
                String valueDate = getCol(cols, colIndex, "value_date");
                String narration = getCol(cols, colIndex, "narration");

                Long amountPaise = NormalizationUtil.parseAmountPaise(amtStr, true);
                if (amountPaise == null) {
                    errors.add(rowError(i, "amount_paise", "not a valid integer", amtStr));
                    continue;
                }

                rows.add(buildRow(clientId, utr, direction, amountPaise, status, valueDate, narration));

                Instant vd = NormalizationUtil.parseInstant(valueDate);
                if (vd != null && (detectedCut == null || vd.isAfter(detectedCut))) detectedCut = vd;
            }
        } catch (Exception e) {
            log.error("BankXLSXAdapter CSV fallback failed for {}: {}", filename, e.getMessage());
            errors.add(rowError(-1, "file", "CSV parse failure: " + e.getMessage(), null));
        }

        return ParseResult.builder().rawRows(rows).errors(errors).detectedCut(detectedCut).schemaVersion("v1").build();
    }

    private Map<String, String> buildRow(String clientId, String utr, String direction,
                                          long amountPaise, String status, String valueDate, String narration) {
        Map<String, String> r = new LinkedHashMap<>();
        r.put("client_id",     NormalizationUtil.normalizeClientId(clientId));
        r.put("utr_reference", utr != null ? utr.strip() : null);
        r.put("direction",     direction != null ? direction.strip().toUpperCase() : null);
        r.put("amount_paise",  String.valueOf(amountPaise));
        r.put("bank_status",   status != null ? status.strip().toUpperCase() : null);
        r.put("value_date",    valueDate != null ? valueDate.strip() : null);
        r.put("narration",     narration);
        return r;
    }

    private String getXlsxCol(Row row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null) return null;
        Cell cell = row.getCell(i);
        return cell == null ? null : cellStr(cell);
    }

    private String getCol(String[] cols, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i >= cols.length) return null;
        String v = cols[i];
        return (v == null || v.isBlank()) ? null : v.strip();
    }

    private String cellStr(Cell cell) {
        if (cell == null) return null;
        String value = dataFormatter.formatCellValue(cell);
        return value == null || value.isBlank() ? null : value.strip();
    }

    private ParseResult emptyResult() {
        return ParseResult.builder().rawRows(List.of()).errors(List.of()).detectedCut(null).schemaVersion("v1").build();
    }

    private ParseResult.RowError rowError(int ri, String f, String m, String rv) {
        return ParseResult.RowError.builder().rowIndex(ri).field(f).message(m).rawValue(rv).build();
    }
}
