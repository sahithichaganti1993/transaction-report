package com.bet99.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.bet99.report.config.ReportProperties;
import com.bet99.report.dto.ReportPage;
import com.bet99.report.dto.ReportSummary;
import com.bet99.report.repository.AccountTranRepository;
import com.bet99.report.repository.ReportQueryBuilder;
import com.bet99.report.repository.TransactionColumnRegistry;
import com.bet99.report.repository.TransactionReportRepository;
import com.bet99.report.web.ReportCriteria;

/**
 * Unit tests for the service layer, with the repository mocked. No Spring
 * context and no database - this is arithmetic and input sanitising.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock private TransactionReportRepository reportRepository;
    @Mock private AccountTranRepository accountTranRepository;
    @Mock private TransactionColumnRegistry columnRegistry;

    private ReportService service;

    @BeforeEach
    void setUp() {
        ReportProperties properties = new ReportProperties();
        ReportQueryBuilder queryBuilder = new ReportQueryBuilder(
                "account_tran", "(t.`AMOUNT_REAL`)", "(t.`BALANCE_REAL`)",
                Arrays.asList("GAME_BET"), Arrays.asList("GAME_WIN"));

        service = new ReportService(reportRepository, accountTranRepository,
                queryBuilder, columnRegistry, properties);
    }

    private ReportCriteria criteria() {
        ReportCriteria c = new ReportCriteria();
        c.setStartDateTime(LocalDateTime.of(2025, 8, 1, 0, 0));
        c.setEndDateTime(LocalDateTime.of(2025, 8, 31, 0, 0));
        return c;
    }

    private ReportSummary summaryOf(long rows) {
        return new ReportSummary(rows, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // --- normalize: the guard on unvalidated request parameters -------------

    @Test
    @DisplayName("an unrecognised sort key is replaced with the default")
    void normalizeRejectsUnknownSort() {
        ReportCriteria c = criteria();
        c.setSort("'; DROP TABLE account_tran; --");

        service.normalize(c);

        assertThat(c.getSort()).isEqualTo("datetime");
    }

    @Test
    @DisplayName("anything other than asc becomes desc")
    void normalizeDirection() {
        ReportCriteria asc = criteria();
        asc.setDir("ASC");
        service.normalize(asc);
        assertThat(asc.getDir()).isEqualTo("asc");

        ReportCriteria nonsense = criteria();
        nonsense.setDir("sideways");
        service.normalize(nonsense);
        assertThat(nonsense.getDir()).isEqualTo("desc");
    }

    @Test
    @DisplayName("an unoffered page size falls back to the default")
    void normalizeClampsPageSize() {
        ReportCriteria c = criteria();
        c.setSize(999_999_999);

        service.normalize(c);

        // Without this the app would try to load the whole table into memory.
        assertThat(c.getSize()).isEqualTo(25);
    }

    @Test
    @DisplayName("a page below 1 becomes 1")
    void normalizeClampsPageNumber() {
        ReportCriteria c = criteria();
        c.setPage(-5);

        service.normalize(c);

        // A negative page would otherwise produce a negative SQL OFFSET.
        assertThat(c.getPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("an offered page size is left alone")
    void normalizeAcceptsValidPageSize() {
        ReportCriteria c = criteria();
        c.setSize(50);

        service.normalize(c);

        assertThat(c.getSize()).isEqualTo(50);
    }

    // --- generate ----------------------------------------------------------

    @Test
    @DisplayName("an empty range skips the row query entirely")
    void emptyRangeSkipsRowQuery() {
        when(reportRepository.findSummary(any())).thenReturn(summaryOf(0));

        ReportPage page = service.generate(criteria());

        assertThat(page.getRows()).isEmpty();
        assertThat(page.getTotalRows()).isZero();
        verify(reportRepository, never()).findRows(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("a page past the end is clamped to the last page")
    void pagePastTheEndIsClamped() {
        when(reportRepository.findSummary(any())).thenReturn(summaryOf(100));
        when(reportRepository.findRows(any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        ReportCriteria c = criteria();
        c.setSize(25);
        c.setPage(999);

        ReportPage page = service.generate(c);

        // 100 rows at 25 per page = 4 pages.
        assertThat(page.getPage()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(4);
    }

    @Test
    @DisplayName("the offset is derived from the clamped page, not the requested one")
    void offsetFollowsTheClampedPage() {
        when(reportRepository.findSummary(any())).thenReturn(summaryOf(100));
        when(reportRepository.findRows(any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        ReportCriteria c = criteria();
        c.setSize(25);
        c.setPage(999);
        service.generate(c);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        verify(reportRepository).findRows(any(), offset.capture(), anyInt());

        // Page 4 of 4, not page 999.
        assertThat(offset.getValue()).isEqualTo(75);
    }

    @Test
    @DisplayName("the second page starts at the right offset")
    void offsetForASecondPage() {
        when(reportRepository.findSummary(any())).thenReturn(summaryOf(100));
        when(reportRepository.findRows(any(), anyInt(), anyInt())).thenReturn(Collections.emptyList());

        ReportCriteria c = criteria();
        c.setSize(25);
        c.setPage(2);
        service.generate(c);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        verify(reportRepository).findRows(any(), offset.capture(), anyInt());

        assertThat(offset.getValue()).isEqualTo(25);
    }

    // --- degradation -------------------------------------------------------

    @Test
    @DisplayName("a failing tran-type lookup degrades to free text rather than failing the page")
    void tranTypesDegradesGracefully() {
        when(accountTranRepository.findDistinctTranTypes())
                .thenThrow(new RuntimeException("database unavailable"));

        // The list only populates an autocomplete; it must not take the page down.
        assertThat(service.tranTypes()).isEmpty();
    }

    // --- default range -----------------------------------------------------

    @Test
    @DisplayName("the default range is the table's own window, widened to whole days")
    void defaultRangeUsesTheDataWindow() {
        when(accountTranRepository.findDataWindow()).thenReturn(List.<Object[]>of(new Object[] {
                Timestamp.valueOf(LocalDateTime.of(2025, 7, 28, 15, 17, 10)),
                Timestamp.valueOf(LocalDateTime.of(2025, 12, 28, 1, 25, 36))
        }));

        LocalDateTime[] range = service.defaultRange();

        // Widened so boundary rows are never clipped.
        assertThat(range[0]).isEqualTo(LocalDateTime.of(2025, 7, 28, 0, 0));
        assertThat(range[1]).isEqualTo(LocalDateTime.of(2025, 12, 29, 0, 0));
    }

    @Test
    @DisplayName("the data window is queried once and then cached")
    void defaultRangeIsCached() {
        when(accountTranRepository.findDataWindow()).thenReturn(List.<Object[]>of(new Object[] {
                Timestamp.valueOf(LocalDateTime.of(2025, 7, 28, 15, 17, 10)),
                Timestamp.valueOf(LocalDateTime.of(2025, 12, 28, 1, 25, 36))
        }));

        service.defaultRange();
        service.defaultRange();
        service.defaultRange();

        verify(accountTranRepository, org.mockito.Mockito.times(1)).findDataWindow();
    }

    @Test
    @DisplayName("a failing window lookup falls back to a configured range")
    void defaultRangeFallsBack() {
        when(accountTranRepository.findDataWindow())
                .thenThrow(new RuntimeException("database unavailable"));

        LocalDateTime[] range = service.defaultRange();

        assertThat(range[0]).isNotNull();
        assertThat(range[1]).isNotNull();
        assertThat(range[0]).isBefore(range[1]);
    }
}
