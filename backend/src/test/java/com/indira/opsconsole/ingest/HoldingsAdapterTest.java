package com.indira.opsconsole.ingest;

import com.indira.opsconsole.ingest.adapter.HoldingsCSVAdapter;
import com.indira.opsconsole.ingest.adapter.ParseResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HoldingsCSVAdapter (no Spring context needed — pure unit test).
 */
class HoldingsAdapterTest {

    private final HoldingsCSVAdapter adapter = new HoldingsCSVAdapter();

    private static final String VALID_CSV =
        "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
        "CLI-001,INE009A01021,EQUITY,INFY,EQ,150,2024-09-16T18:00:00\n" +
        "CLI-002,INE040A01034,EQUITY,HDFCBANK,EQ,200,2024-09-16T18:00:00\n";

    private static final String REORDERED_HEADERS_CSV =
        "isin,settled_qty,cut_at,client_id,series,exchange_symbol,position_type\n" +
        "INE009A01021,150,2024-09-16T18:00:00,CLI-001,EQ,INFY,EQUITY\n";

    private static final String MISSING_REQUIRED_CSV =
        "client_id,isin,position_type,exchange_symbol,series,settled_qty,cut_at\n" +
        ",INE009A01021,EQUITY,INFY,EQ,150,2024-09-16T18:00:00\n" +   // missing client_id
        "CLI-002,INE040A01034,EQUITY,HDFCBANK,EQ,NOT_A_NUMBER,2024-09-16T18:00:00\n";  // bad qty

    @Test
    @DisplayName("Valid CSV parses all rows correctly")
    void validCsv_allRowsParsed() {
        ParseResult result = adapter.parse(VALID_CSV.getBytes(), "holdings.csv");
        assertThat(result.getRawRows()).hasSize(2);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getDetectedCut()).isNotNull();
    }

    @Test
    @DisplayName("Column order does not matter — canonical header matching")
    void reorderedHeaders_parsedCorrectly() {
        ParseResult result = adapter.parse(REORDERED_HEADERS_CSV.getBytes(), "holdings_reorder.csv");
        assertThat(result.getRawRows()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getRawRows().get(0).get("client_id")).isEqualTo("CLI-001");
        assertThat(result.getRawRows().get(0).get("isin")).isEqualTo("INE009A01021");
        assertThat(result.getRawRows().get(0).get("settled_qty")).isEqualTo("150");
    }

    @Test
    @DisplayName("Rows with missing client_id or invalid qty are quarantined as errors")
    void invalidRows_quarantined() {
        ParseResult result = adapter.parse(MISSING_REQUIRED_CSV.getBytes(), "bad.csv");
        // Both rows should be quarantined
        assertThat(result.getRawRows()).isEmpty();
        assertThat(result.getErrors()).hasSize(2);
        assertThat(result.getErrors().get(0).getField()).isEqualTo("client_id");
        assertThat(result.getErrors().get(1).getField()).isEqualTo("settled_qty");
    }

    @Test
    @DisplayName("ISIN is always uppercased in normalized output")
    void isin_uppercasedInOutput() {
        String csv = "client_id,isin,position_type,settled_qty,cut_at\n" +
                     "CLI-001,ine009a01021,EQUITY,100,2024-09-16T18:00:00\n";
        ParseResult result = adapter.parse(csv.getBytes(), "lower.csv");
        assertThat(result.getRawRows().get(0).get("isin")).isEqualTo("INE009A01021");
    }

    @Test
    @DisplayName("Empty CSV returns empty result with no errors")
    void emptyCsv_returnsEmpty() {
        ParseResult result = adapter.parse("client_id,isin\n".getBytes(), "empty.csv");
        assertThat(result.getRawRows()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }
}
