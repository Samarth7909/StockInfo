package com.indira.opsconsole.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indira.opsconsole.domain.entity.*;
import com.indira.opsconsole.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Persists normalised rows from a ParseResult into domain tables.
 * Each method handles one stream's rows.
 * Raw JSON is stored in raw_rows before domain persistence so provenance is always preserved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RowPersistenceService {

    private final RawRowRepository              rawRowRepo;
    private final NormalizedHoldingRepository   holdingRepo;
    private final DpPendingMovementRepository   pendingRepo;
    private final CashEventRepository           cashEventRepo;
    private final BankEntryRepository           bankEntryRepo;
    private final ExchangeReferenceRepository   exchangeRefRepo;
    private final ObjectMapper                  objectMapper;

    // ── HOLDINGS ──────────────────────────────────────────────────────────────

    public void persistHoldingsRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, String> row = rows.get(idx);
            RawRow rawRow = saveRawRow(sourceFile, idx, row);

            NormalizedHolding h = NormalizedHolding.builder()
                .sourceFile(sourceFile)
                .rawRow(rawRow)
                .clientId(row.get("client_id"))
                .isin(row.get("isin"))
                .positionType(row.getOrDefault("position_type", "EQUITY"))
                .cutAt(parseInstant(row.get("cut_at")))
                .settledQty(parseLong(row.get("settled_qty"), 0L))
                .exchangeSymbol(row.get("exchange_symbol"))
                .series(row.get("series"))
                .dataSource("INTERNAL")
                .build();
            holdingRepo.save(h);
        }
    }

    // ── DP EXTRACT ────────────────────────────────────────────────────────────

    public void persistDpRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, String> row = rows.get(idx);
            RawRow rawRow = saveRawRow(sourceFile, idx, row);
            String rowType = row.getOrDefault("row_type", "SETTLED");

            if ("PENDING".equals(rowType)) {
                DpPendingMovement pm = DpPendingMovement.builder()
                    .sourceFile(sourceFile)
                    .rawRow(rawRow)
                    .clientId(row.get("client_id"))
                    .isin(row.get("isin"))
                    .positionType(row.getOrDefault("position_type", "EQUITY"))
                    .cutAt(parseInstant(row.get("cut_at")))
                    .pendingQty(parseLong(row.get("pending_qty"), 0L))
                    .movementType(row.get("movement_state"))
                    .exchangeSymbol(row.get("exchange_symbol"))
                    .build();
                pendingRepo.save(pm);
            } else {
                NormalizedHolding h = NormalizedHolding.builder()
                    .sourceFile(sourceFile)
                    .rawRow(rawRow)
                    .clientId(row.get("client_id"))
                    .isin(row.get("isin"))
                    .positionType(row.getOrDefault("position_type", "EQUITY"))
                    .cutAt(parseInstant(row.get("cut_at")))
                    .settledQty(parseLong(row.get("settled_qty"), 0L))
                    .exchangeSymbol(row.get("exchange_symbol"))
                    .dataSource("DP")
                    .build();
                holdingRepo.save(h);
            }
        }
    }

    // ── CASH LEDGER ───────────────────────────────────────────────────────────

    public void persistCashRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, String> row = rows.get(idx);
            RawRow rawRow = saveRawRow(sourceFile, idx, row);

            CashEvent ev = CashEvent.builder()
                .sourceFile(sourceFile)
                .rawRow(rawRow)
                .clientId(row.get("client_id"))
                .eventId(row.get("event_id"))
                .amountPaise(parseLong(row.get("amount_paise"), 0L))
                .state(row.getOrDefault("state", "PENDING"))
                .effectiveAt(parseInstant(row.get("effective_at")))
                .narration(row.get("narration"))
                .build();
            cashEventRepo.save(ev);
        }
    }

    // ── BANK CONFIRMATION ─────────────────────────────────────────────────────

    public void persistBankRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, String> row = rows.get(idx);
            RawRow rawRow = saveRawRow(sourceFile, idx, row);

            BankEntry entry = BankEntry.builder()
                .sourceFile(sourceFile)
                .rawRow(rawRow)
                .clientId(row.get("client_id"))
                .utrReference(row.get("utr_reference"))
                .direction(row.get("direction"))
                .amountPaise(parseLong(row.get("amount_paise"), 0L))
                .bankStatus(row.get("bank_status"))
                .valueDate(parseDate(row.get("value_date")))
                .narration(row.get("narration"))
                .humanMatched(false)   // never auto-matched
                .build();
            bankEntryRepo.save(entry);
        }
    }

    // ── EXCHANGE REFERENCE ────────────────────────────────────────────────────

    public void persistExchangeRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            Map<String, String> row = rows.get(idx);
            RawRow rawRow = saveRawRow(sourceFile, idx, row);

            String priceStr = row.get("reference_price_paise");
            Long price = priceStr != null ? parseLongNullable(priceStr) : null;

            ExchangeReference ref = ExchangeReference.builder()
                .sourceFile(sourceFile)
                .rawRow(rawRow)
                .isin(row.get("isin"))
                .symbol(row.get("symbol"))
                .series(row.get("series"))
                .exchange(row.get("exchange"))
                .referencePricePaise(price)
                .effectiveDate(parseDate(row.get("effective_date")))
                .build();
            exchangeRefRepo.save(ref);
        }
    }

    public void persistRawRows(List<Map<String, String>> rows, SourceFile sourceFile) {
        for (int idx = 0; idx < rows.size(); idx++) {
            saveRawRow(sourceFile, idx, rows.get(idx));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RawRow saveRawRow(SourceFile sf, int idx, Map<String, String> row) {
        String json;
        try {
            json = objectMapper.writeValueAsString(row);
        } catch (Exception e) {
            json = row.toString();
        }
        RawRow rr = RawRow.builder()
            .sourceFile(sf)
            .rowIndex(idx)
            .rawJson(json)
            .build();
        return rawRowRepo.save(rr);
    }

    private Instant parseInstant(String v) {
        if (v == null) return null;
        try { return Instant.parse(v); } catch (Exception e) { return null; }
    }

    private LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDate.parse(v.strip()); } catch (Exception e) { return null; }
    }

    private long parseLong(String v, long defaultVal) {
        if (v == null) return defaultVal;
        try { return Long.parseLong(v.strip()); } catch (NumberFormatException e) { return defaultVal; }
    }

    private Long parseLongNullable(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Long.parseLong(v.strip()); } catch (NumberFormatException e) { return null; }
    }
}
