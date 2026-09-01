package com.bet99.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bet99.report.config.ReportProperties;
import com.bet99.report.dto.TransactionRow;
import com.bet99.report.repository.TransactionReportRepository;
import com.bet99.report.web.ReportCriteria;

/**
 * Unit tests for CSV generation: RFC 4180 quoting, the spreadsheet
 * formula-injection guard, and chunked streaming.
 */
class CsvExportServiceTest {

    private ReportCriteria criteria() {
        ReportCriteria c = new ReportCriteria();
        c.setStartDateTime(LocalDateTime.of(2025, 7, 28, 0, 0));
        c.setEndDateTime(LocalDateTime.of(2025, 12, 29, 0, 0));
        return c;
    }

    private TransactionRow row(long id) {
        return new TransactionRow(id, 100L, LocalDateTime.of(2025, 8, 1, 10, 0),
                "GAME_BET", "500010000001", "110000001", "slots-aurora",
                new BigDecimal("-10.00"), new BigDecimal("100.00"));
    }

    // --- escaping ----------------------------------------------------------

    @Test
    @DisplayName("plain values are written unquoted")
    void plainValuesAreUnquoted() {
        assertThat(CsvExportService.escape("GAME_BET")).isEqualTo("GAME_BET");
        assertThat(CsvExportService.escape(123)).isEqualTo("123");
    }

    @Test
    @DisplayName("null becomes an empty field, not the text null")
    void nullBecomesEmpty() {
        assertThat(CsvExportService.escape(null)).isEmpty();
    }

    @Test
    @DisplayName("values containing a comma, quote or newline are quoted")
    void separatorsForceQuoting() {
        assertThat(CsvExportService.escape("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvExportService.escape("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    @DisplayName("an embedded quote is doubled, per RFC 4180")
    void embeddedQuotesAreDoubled() {
        assertThat(CsvExportService.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    }

    @Test
    @DisplayName("text that a spreadsheet would treat as a formula is neutralised")
    void formulaInjectionIsGuarded() {
        // Opened in Excel or Sheets, a leading = + - or @ makes the cell a
        // formula. Prefixing with an apostrophe forces it to stay text.
        assertThat(CsvExportService.escape("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvExportService.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(CsvExportService.escape("+cmd")).isEqualTo("'+cmd");
    }

    @Test
    @DisplayName("a negative number stays numeric and is never prefixed")
    void negativeNumbersAreNotTreatedAsFormulas() {
        // The guard applies only to non-numbers. A negative amount must remain
        // a number in the spreadsheet, or the totals cannot be summed.
        assertThat(CsvExportService.escape(new BigDecimal("-10.00"))).isEqualTo("-10.00");
        assertThat(CsvExportService.escape(-42)).isEqualTo("-42");
    }

    // --- writing -----------------------------------------------------------

    @Test
    @DisplayName("the header row names the nine report columns")
    void headerRowIsWritten() throws IOException {
        TransactionReportRepository repo = mock(TransactionReportRepository.class);
        when(repo.findRows(any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        StringWriter out = new StringWriter();
        service(repo, new ReportProperties()).write(criteria(), out);

        assertThat(out.toString().split("\r\n")[0])
                .isEqualTo("id,account_id,datetime,tran_type,platform_tran_id,"
                        + "game_tran_id,game_id,amount,balance");
    }

    @Test
    @DisplayName("rows are written with CRLF line endings")
    void rowsUseCrlf() throws IOException {
        TransactionReportRepository repo = mock(TransactionReportRepository.class);
        when(repo.findRows(any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(1)))
                .thenReturn(Collections.emptyList());

        StringWriter out = new StringWriter();
        long written = service(repo, new ReportProperties()).write(criteria(), out);

        assertThat(written).isEqualTo(1);
        assertThat(out.toString()).contains("\r\n");
        assertThat(out.toString()).contains("1,100,2025-08-01 10:00:00,GAME_BET");
    }

    @Test
    @DisplayName("the export is fetched in chunks rather than all at once")
    void rowsAreFetchedInChunks() throws IOException {
        ReportProperties properties = new ReportProperties();
        properties.setCsvChunkSize(2);

        // Five rows at a chunk size of two: 2, 2, 1 - and the short final
        // chunk ends the loop without a further round trip.
        TransactionReportRepository repo = mock(TransactionReportRepository.class);
        when(repo.findRows(any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(1), row(2)))
                .thenReturn(List.of(row(3), row(4)))
                .thenReturn(List.of(row(5)));

        StringWriter out = new StringWriter();
        long written = service(repo, properties).write(criteria(), out);

        assertThat(written).isEqualTo(5);
        assertThat(dataLines(out)).hasSize(5);
    }

    @Test
    @DisplayName("the export stops at the configured row cap")
    void exportStopsAtTheCap() throws IOException {
        ReportProperties properties = new ReportProperties();
        properties.setCsvChunkSize(10);
        properties.setCsvMaxRows(3);

        TransactionReportRepository repo = mock(TransactionReportRepository.class);
        when(repo.findRows(any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int limit = invocation.getArgument(2);
            List<TransactionRow> rows = new ArrayList<>();
            for (int i = 0; i < limit; i++) {
                rows.add(row(i + 1));
            }
            return rows;
        });

        StringWriter out = new StringWriter();
        long written = service(repo, properties).write(criteria(), out);

        assertThat(written).isEqualTo(3);
    }

    @Test
    @DisplayName("the filename carries the date range")
    void fileNameCarriesTheRange() {
        String name = service(mock(TransactionReportRepository.class), new ReportProperties())
                .fileName(criteria());

        assertThat(name).isEqualTo("game-transactions-2025-07-28_to_2025-12-29.csv");
    }

    // -----------------------------------------------------------------------

    private CsvExportService service(TransactionReportRepository repo, ReportProperties properties) {
        ReportService reportService = mock(ReportService.class);
        return new CsvExportService(repo, reportService, properties);
    }

    private static List<String> dataLines(StringWriter out) {
        List<String> lines = new ArrayList<>(List.of(out.toString().split("\r\n")));
        lines.remove(0);   // header
        lines.removeIf(String::isEmpty);
        return lines;
    }
}
