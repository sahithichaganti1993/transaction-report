package com.bet99.report.service;

import com.bet99.report.config.ReportProperties;
import com.bet99.report.dto.ReportPage;
import com.bet99.report.dto.ReportSummary;
import com.bet99.report.dto.TransactionRow;
import com.bet99.report.repository.AccountTranRepository;
import com.bet99.report.repository.ReportQueryBuilder;
import com.bet99.report.repository.TransactionColumnRegistry;
import com.bet99.report.repository.TransactionReportRepository;
import com.bet99.report.web.ReportCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Application logic for the report: sanitises paging/sorting input, runs the
 * count+totals query first, and skips the row query entirely when the range is
 * empty.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final TransactionReportRepository reportRepository;
    private final AccountTranRepository accountTranRepository;
    private final ReportQueryBuilder queryBuilder;
    private final TransactionColumnRegistry columnRegistry;
    private final ReportProperties properties;

    /** Cached on first use; the table's date window does not move during a session. */
    private volatile LocalDateTime[] dataWindow;

    public ReportService(TransactionReportRepository reportRepository,
                         AccountTranRepository accountTranRepository,
                         ReportQueryBuilder queryBuilder,
                         TransactionColumnRegistry columnRegistry,
                         ReportProperties properties) {
        this.reportRepository = reportRepository;
        this.accountTranRepository = accountTranRepository;
        this.queryBuilder = queryBuilder;
        this.columnRegistry = columnRegistry;
        this.properties = properties;
    }

    /**
     * Normalises {@code sort}, {@code dir}, {@code page} and {@code size} in
     * place so the view renders the same state the query actually used.
     */
    public void normalize(ReportCriteria criteria) {
        criteria.setSort(queryBuilder.normalizeSort(criteria.getSort()));
        criteria.setDir("asc".equalsIgnoreCase(criteria.getDir()) ? "asc" : "desc");
        if (!properties.getPageSizes().contains(criteria.getSize())) {
            criteria.setSize(properties.defaultPageSize());
        }
        if (criteria.getPage() < 1) {
            criteria.setPage(1);
        }
    }

    @Transactional(readOnly = true)
    public ReportPage generate(ReportCriteria criteria) {
        normalize(criteria);

        ReportSummary summary = reportRepository.findSummary(criteria);
        if (summary.getTotalRows() == 0) {
            return new ReportPage(Collections.emptyList(), summary, 1, criteria.getSize());
        }

        // Clamp a page number that has run past the end of the result set -
        // otherwise a bookmarked deep link renders an empty grid.
        int totalPages = (int) ((summary.getTotalRows() + criteria.getSize() - 1) / criteria.getSize());
        if (criteria.getPage() > totalPages) {
            criteria.setPage(totalPages);
        }

        int offset = (criteria.getPage() - 1) * criteria.getSize();
        List<TransactionRow> rows = reportRepository.findRows(criteria, offset, criteria.getSize());
        return new ReportPage(rows, summary, criteria.getPage(), criteria.getSize());
    }

    /** Suggestions for the TRAN_TYPE filter; an empty list just means free text. */
    @Transactional(readOnly = true)
    public List<String> tranTypes() {
        try {
            return accountTranRepository.findDistinctTranTypes();
        } catch (RuntimeException e) {
            log.warn("Could not load distinct TRAN_TYPE values, falling back to free-text filter", e);
            return Collections.emptyList();
        }
    }

    /**
     * Range the search form is prefilled with on a first visit: the table's own
     * date window, so the report shows data immediately instead of an empty
     * grid. The supplied dataset ends in December 2025, which a naive
     * "last 7 days" default would miss entirely.
     */
    @Transactional(readOnly = true)
    public LocalDateTime[] defaultRange() {
        LocalDateTime[] window = dataWindow;
        if (window == null) {
            window = loadDataWindow();
            dataWindow = window;
        }
        return new LocalDateTime[] { window[0], window[1] };
    }

    private LocalDateTime[] loadDataWindow() {
        try {
            List<Object[]> result = accountTranRepository.findDataWindow();
            if (!result.isEmpty()) {
                LocalDateTime min = toLocalDateTime(result.get(0)[0]);
                LocalDateTime max = toLocalDateTime(result.get(0)[1]);
                if (min != null && max != null) {
                    // Widen to whole days so the boundary rows are never clipped.
                    return new LocalDateTime[] { min.with(LocalTime.MIDNIGHT), max.plusDays(1).with(LocalTime.MIDNIGHT) };
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not determine the table's date window, using the configured fallback", e);
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        return new LocalDateTime[] { now.minusDays(properties.getDefaultRangeDays()).with(LocalTime.MIDNIGHT), now };
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return null;
    }

    public List<Integer> pageSizes() {
        return properties.getPageSizes();
    }

    /** Shown in the page footer so it is always obvious what the two totals are made of. */
    public List<String> amountColumns() {
        return columnRegistry.getAmountColumns();
    }

    public List<String> balanceColumns() {
        return columnRegistry.getBalanceColumns();
    }
}
