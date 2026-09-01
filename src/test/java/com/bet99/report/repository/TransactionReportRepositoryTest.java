package com.bet99.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.bet99.report.config.ReportProperties;
import com.bet99.report.dto.ReportSummary;
import com.bet99.report.dto.TransactionRow;
import com.bet99.report.web.ReportCriteria;

/**
 * Runs the generated SQL against a real database engine - H2 in MySQL mode -
 * using a fixture that mirrors the supplied schema's conventions.
 *
 * <p>{@link ReportQueryBuilderTest} asserts what the SQL <em>says</em>. This
 * asserts what it <em>does</em>: that it parses, executes, and returns the rows
 * and totals expected.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableConfigurationProperties(ReportProperties.class)
@EntityScan("com.bet99.report.domain")
@Import({ TransactionReportRepositoryTest.TestBeans.class, TransactionReportRepository.class })
class TransactionReportRepositoryTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        TransactionColumnRegistry registry(javax.sql.DataSource dataSource, ReportProperties properties) {
            return new TransactionColumnRegistry(dataSource, properties);
        }

        @Bean
        ReportQueryBuilder reportQueryBuilder(TransactionColumnRegistry registry, ReportProperties properties) {
            return new ReportQueryBuilder(
                    properties.getTable(),
                    registry.amountExpression(ReportQueryBuilder.ALIAS),
                    registry.balanceExpression(ReportQueryBuilder.ALIAS),
                    properties.getBetTranTypes(),
                    properties.getWinTranTypes());
        }
    }

    @Autowired
    private TransactionReportRepository repository;

    @Autowired
    private AccountTranRepository accountTranRepository;

    /** 2025-08-01 .. 2025-08-31: five of the six fixture rows. */
    private ReportCriteria august() {
        ReportCriteria c = new ReportCriteria();
        c.setStartDateTime(LocalDateTime.of(2025, 8, 1, 0, 0));
        c.setEndDateTime(LocalDateTime.of(2025, 8, 31, 23, 59, 59));
        c.setSort("id");
        c.setDir("asc");
        return c;
    }

    private static List<Long> idsOf(List<TransactionRow> rows) {
        return rows.stream().map(TransactionRow::getId).toList();
    }

    // -----------------------------------------------------------------------

    @Test
    @DisplayName("the date range excludes rows outside it")
    void dateRangeFilters() {
        List<TransactionRow> rows = repository.findRows(august(), 0, 100);

        // Row 6 is in September.
        assertThat(idsOf(rows)).containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("the account filter narrows to one account")
    void accountFilter() {
        ReportCriteria c = august();
        c.setAccountId(200L);

        assertThat(idsOf(repository.findRows(c, 0, 100))).containsExactly(4L, 5L);
    }

    @Test
    @DisplayName("the tran type filter is an exact match")
    void tranTypeFilter() {
        ReportCriteria c = august();
        c.setTranType("GAME_WIN");

        assertThat(idsOf(repository.findRows(c, 0, 100))).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("text filters match on prefix")
    void prefixFilter() {
        ReportCriteria c = august();
        c.setGameId("slots-");

        assertThat(idsOf(repository.findRows(c, 0, 100))).containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("a user-typed wildcard is matched literally, not as a wildcard")
    void wildcardIsEscaped() {
        ReportCriteria c = august();
        c.setGameId("slots-%");

        // If % leaked through as a wildcard this would return the three
        // slots- rows; escaped, it matches nothing.
        assertThat(repository.findRows(c, 0, 100)).isEmpty();
    }

    @Test
    @DisplayName("offset and limit page through the result set without overlap")
    void pagingIsStable() {
        List<Long> page1 = idsOf(repository.findRows(august(), 0, 2));
        List<Long> page2 = idsOf(repository.findRows(august(), 2, 2));

        assertThat(page1).containsExactly(1L, 2L);
        assertThat(page2).containsExactly(3L, 4L);
        assertThat(page1).doesNotContainAnyElementsOf(page2);
    }

    @Test
    @DisplayName("descending sort reverses the order")
    void descendingSort() {
        ReportCriteria c = august();
        c.setDir("desc");

        assertThat(idsOf(repository.findRows(c, 0, 100))).containsExactly(5L, 4L, 3L, 2L, 1L);
    }

    @Test
    @DisplayName("a null money column does not poison the row's amount")
    void nullAmountIsCoalesced() {
        ReportCriteria c = august();
        c.setAccountId(100L);

        List<TransactionRow> rows = repository.findRows(c, 0, 100);
        TransactionRow rowWithNullAmountReal = rows.stream()
                .filter(r -> r.getId() == 3L)
                .findFirst()
                .orElseThrow();

        // AMOUNT_REAL is null; AMOUNT_PLAYABLE_BONUS is 5.00. Without COALESCE
        // the whole sum would be null.
        assertThat(rowWithNullAmountReal.getAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("amount sums every AMOUNT_* column, including loyalty points")
    void amountSumsAllColumns() {
        ReportCriteria c = august();
        c.setAccountId(200L);
        List<TransactionRow> rows = repository.findRows(c, 0, 100);

        // Row 4: AMOUNT_REAL 8.00, everything else 0.
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo("8.00");
        // Row 4 balance: BALANCE_REAL 58.00 + BALANCE_RAW_LOYALTY 500 = 558.00
        assertThat(rows.get(0).getBalance()).isEqualByComparingTo("558.00");
    }

    // --- summary -----------------------------------------------------------

    @Test
    @DisplayName("the summary counts every matching row")
    void summaryCountsRows() {
        assertThat(repository.findSummary(august()).getTotalRows()).isEqualTo(5);
    }

    @Test
    @DisplayName("bet and win totals are reported as magnitudes so net reads correctly")
    void summaryTotals() {
        ReportSummary summary = repository.findSummary(august());

        // Bets: row 1 (-10.00) and row 3 (null + 5.00 bonus) -> raw -5.00
        assertThat(summary.getBetSum()).isEqualByComparingTo("5.00");
        // Wins: row 2 (20.00) and row 4 (8.00)
        assertThat(summary.getWinSum()).isEqualByComparingTo("28.00");
        // net = win - bet
        assertThat(summary.getNet()).isEqualByComparingTo("23.00");
    }

    @Test
    @DisplayName("transaction types outside bet/win are counted but not classified")
    void otherTypesAreCountedOnly() {
        ReportCriteria c = august();
        c.setTranType("ROLLBACK");

        ReportSummary summary = repository.findSummary(c);

        assertThat(summary.getTotalRows()).isEqualTo(1);
        assertThat(summary.getBetSum()).isEqualByComparingTo("0");
        assertThat(summary.getWinSum()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("an empty range returns zeroes rather than nulls")
    void emptyRangeReturnsZeroes() {
        ReportCriteria c = august();
        c.setStartDateTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        c.setEndDateTime(LocalDateTime.of(2030, 1, 2, 0, 0));

        ReportSummary summary = repository.findSummary(c);

        assertThat(summary.getTotalRows()).isZero();
        assertThat(summary.getBetSum()).isEqualByComparingTo("0");
        assertThat(summary.getNet()).isEqualByComparingTo("0");
    }

    // --- the JPA entity path ------------------------------------------------

    @Test
    @DisplayName("the entity mapping resolves distinct TRAN_TYPE values")
    void entityMappingResolvesTranTypes() {
        assertThat(accountTranRepository.findDistinctTranTypes())
                .containsExactly("GAME_BET", "GAME_WIN", "ROLLBACK");
    }

    @Test
    @DisplayName("the entity mapping resolves the table's date window")
    void entityMappingResolvesDataWindow() {
        List<Object[]> window = accountTranRepository.findDataWindow();

        assertThat(window).hasSize(1);
        assertThat(window.get(0)[0]).isNotNull();
        assertThat(window.get(0)[1]).isNotNull();
    }
}
