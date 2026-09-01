package com.bet99.report.service;

import com.bet99.report.config.ReportProperties;
import com.bet99.report.dto.TransactionRow;
import com.bet99.report.repository.TransactionReportRepository;
import com.bet99.report.web.ReportCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;

/**
 * CSV export of the current filter + sort, ignoring pagination.
 *
 * <p>Rows are pulled in chunks and written straight to the response writer, so
 * a multi-hundred-thousand row export never materialises in the heap.
 */
@Service
public class CsvExportService {

    private static final String[] HEADERS = {
            "id", "account_id", "datetime", "tran_type",
            "platform_tran_id", "game_tran_id", "game_id", "amount", "balance"
    };

    private final TransactionReportRepository reportRepository;
    private final ReportService reportService;
    private final ReportProperties properties;

    public CsvExportService(TransactionReportRepository reportRepository,
                            ReportService reportService,
                            ReportProperties properties) {
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.properties = properties;
    }

    /** @return number of data rows written. */
    @Transactional(readOnly = true)
    public long write(ReportCriteria criteria, Writer writer) throws IOException {
        reportService.normalize(criteria);

        writeRow(writer, (Object[]) HEADERS);

        int chunkSize = Math.max(properties.getCsvChunkSize(), 1);
        int maxRows = Math.max(properties.getCsvMaxRows(), 1);
        long written = 0;
        int offset = 0;

        while (written < maxRows) {
            int limit = (int) Math.min(chunkSize, maxRows - written);
            List<TransactionRow> chunk = reportRepository.findRows(criteria, offset, limit);
            if (chunk.isEmpty()) {
                break;
            }
            for (TransactionRow row : chunk) {
                writeRow(writer,
                        row.getId(),
                        row.getAccountId(),
                        row.getDatetimeText(),
                        row.getTranType(),
                        row.getPlatformTranId(),
                        row.getGameTranId(),
                        row.getGameId(),
                        scale(row.getAmount()),
                        scale(row.getBalance()));
            }
            written += chunk.size();
            offset += chunk.size();
            writer.flush();

            if (chunk.size() < limit) {
                break;
            }
        }
        return written;
    }

    public String fileName(ReportCriteria criteria) {
        return "game-transactions-"
                + criteria.getStartDateTime().toLocalDate()
                + "_to_"
                + criteria.getEndDateTime().toLocalDate()
                + ".csv";
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void writeRow(Writer writer, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escape(values[i]));
        }
        writer.write("\r\n");
    }

    /** RFC 4180 quoting, plus a guard against spreadsheet formula injection. */
    static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Formula-injection guard, but never on numbers - a negative amount
        // must stay numeric when the CSV is opened in a spreadsheet.
        if (!(value instanceof Number) && !text.isEmpty()
                && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
