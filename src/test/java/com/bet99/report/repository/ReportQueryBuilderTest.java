package com.bet99.report.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.bet99.report.web.ReportCriteria;

/**
 * Asserts the SQL that {@link ReportQueryBuilder} generates.
 *
 * <p>No Spring context and no database: the builder is a plain object that is
 * handed its SQL expressions, so the generated statement can be inspected
 * directly. That is the reason the class has no framework dependencies.
 */
class ReportQueryBuilderTest {

    private static final String AMOUNT = "(COALESCE(t.`AMOUNT_REAL`, 0) + COALESCE(t.`AMOUNT_FREE_BET`, 0))";
    private static final String BALANCE = "(COALESCE(t.`BALANCE_REAL`, 0))";

    private final ReportQueryBuilder builder = new ReportQueryBuilder(
            "account_tran", AMOUNT, BALANCE,
            Arrays.asList("GAME_BET"), Arrays.asList("GAME_WIN"));

    /** A criteria with only the mandatory date range set. */
    private ReportCriteria criteria() {
        ReportCriteria c = new ReportCriteria();
        c.setStartDateTime(LocalDateTime.of(2025, 7, 28, 0, 0));
        c.setEndDateTime(LocalDateTime.of(2025, 12, 29, 0, 0));
        return c;
    }

    // -----------------------------------------------------------------------
    // Sort whitelist - the injection defence for the one part of the query
    // that cannot use a bind parameter.
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("sort whitelist")
    class SortWhitelist {

        @Test
        @DisplayName("every documented sort key is accepted")
        void allDocumentedKeysAreSortable() {
            assertThat(builder.sortableKeys()).containsExactlyInAnyOrder(
                    "id", "accountId", "datetime", "tranType",
                    "platformTranId", "gameTranId", "gameId", "amount", "balance");
        }

        @Test
        @DisplayName("an unknown sort key falls back to the default instead of throwing")
        void unknownKeyFallsBackToDefault() {
            assertThat(builder.normalizeSort("nonsense")).isEqualTo(ReportQueryBuilder.DEFAULT_SORT);
            assertThat(builder.normalizeSort(null)).isEqualTo(ReportQueryBuilder.DEFAULT_SORT);
            assertThat(builder.normalizeSort("")).isEqualTo(ReportQueryBuilder.DEFAULT_SORT);
        }

        @Test
        @DisplayName("a SQL injection attempt in the sort key never reaches the query text")
        void injectionAttemptInSortKeyIsNotInterpolated() {
            ReportCriteria c = criteria();
            c.setSort("id; DROP TABLE account_tran; --");

            String sql = builder.rows(c).getSql();

            assertThat(sql).doesNotContain("DROP");
            assertThat(sql).doesNotContain("--");
            // Silently degraded to the default sort rather than erroring.
            assertThat(sql).contains("ORDER BY t.`DATETIME`");
        }

        @Test
        @DisplayName("sorting by a derived column orders by the whole expression")
        void derivedColumnSortsByExpression() {
            ReportCriteria c = criteria();
            c.setSort("amount");

            assertThat(builder.rows(c).getSql()).contains("ORDER BY " + AMOUNT);
        }
    }

    // -----------------------------------------------------------------------
    // ORDER BY
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("order by")
    class OrderBy {

        @Test
        @DisplayName("direction follows the criteria")
        void directionFollowsCriteria() {
            ReportCriteria asc = criteria();
            asc.setSort("datetime");
            asc.setDir("asc");
            assertThat(builder.rows(asc).getSql()).contains("ORDER BY t.`DATETIME` ASC");

            ReportCriteria desc = criteria();
            desc.setSort("datetime");
            desc.setDir("desc");
            assertThat(builder.rows(desc).getSql()).contains("ORDER BY t.`DATETIME` DESC");
        }

        @Test
        @DisplayName("ID is appended as a tie-breaker so paging is stable")
        void tieBreakerKeepsPagingStable() {
            ReportCriteria c = criteria();
            c.setSort("tranType");
            c.setDir("asc");

            // Without this, rows sharing a TRAN_TYPE have no defined order and
            // the same row can appear on two different pages.
            assertThat(builder.rows(c).getSql()).contains("ORDER BY t.TRAN_TYPE ASC, t.ID ASC");
        }

        @Test
        @DisplayName("no redundant tie-breaker when already sorting by ID")
        void noTieBreakerWhenSortingById() {
            ReportCriteria c = criteria();
            c.setSort("id");
            c.setDir("desc");

            assertThat(builder.rows(c).getSql()).endsWith("ORDER BY t.ID DESC");
        }
    }

    // -----------------------------------------------------------------------
    // WHERE clause - every value must be a bind parameter.
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("where clause")
    class Where {

        @Test
        @DisplayName("the date range is always bound, never inlined")
        void dateRangeIsAlwaysBound() {
            SqlQuery q = builder.rows(criteria());

            assertThat(q.getSql())
                    .contains("WHERE t.`DATETIME` >= :startDateTime")
                    .contains("AND t.`DATETIME` <= :endDateTime");
            assertThat(q.getParameters())
                    .containsEntry("startDateTime", LocalDateTime.of(2025, 7, 28, 0, 0))
                    .containsEntry("endDateTime", LocalDateTime.of(2025, 12, 29, 0, 0));
        }

        @Test
        @DisplayName("optional filters are omitted entirely when not supplied")
        void unsetFiltersAreOmitted() {
            SqlQuery q = builder.rows(criteria());

            // Assert the absence of the predicates, not of the column names -
            // ACCOUNT_ID and TRAN_TYPE are both in the SELECT list regardless.
            assertThat(q.getSql())
                    .doesNotContain("AND t.ACCOUNT_ID")
                    .doesNotContain("LIKE")
                    .doesNotContain("AND t.TRAN_TYPE");
            assertThat(q.getParameters()).containsOnlyKeys("startDateTime", "endDateTime");
        }

        @Test
        @DisplayName("each supplied filter adds one predicate and one bind parameter")
        void suppliedFiltersAddPredicates() {
            ReportCriteria c = criteria();
            c.setAccountId(2203L);
            c.setGameId("429");
            c.setTranType("GAME_WIN");

            SqlQuery q = builder.rows(c);

            assertThat(q.getSql())
                    .contains("AND t.ACCOUNT_ID = :accountId")
                    .contains("AND t.GAME_ID LIKE :gameId")
                    .contains("AND t.TRAN_TYPE = :tranType");
            assertThat(q.getParameters())
                    .containsEntry("accountId", 2203L)
                    .containsEntry("gameId", "429%")
                    .containsEntry("tranType", "GAME_WIN");
        }

        @Test
        @DisplayName("text filters are prefix matches so they can use an index")
        void textFiltersArePrefixMatches() {
            ReportCriteria c = criteria();
            c.setPlatformTranId("50001");

            // "50001%" and not "%50001%" - a leading wildcard cannot use an index.
            assertThat(builder.rows(c).getParameters()).containsEntry("platformTranId", "50001%");
        }

        @Test
        @DisplayName("a value containing SQL syntax is bound, not concatenated")
        void injectionAttemptInAValueIsBound() {
            ReportCriteria c = criteria();
            c.setGameTranId("x'; DROP TABLE account_tran; --");

            SqlQuery q = builder.rows(c);

            assertThat(q.getSql()).doesNotContain("DROP");
            assertThat(q.getSql()).contains("AND t.GAME_TRAN_ID LIKE :gameTranId");
            assertThat(q.getParameters().get("gameTranId").toString()).contains("DROP TABLE");
        }
    }

    // -----------------------------------------------------------------------
    // LIKE escaping - a correctness concern, distinct from injection safety.
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("like escaping")
    class LikeEscaping {

        @Test
        @DisplayName("wildcards typed by the user become literal characters")
        void userWildcardsAreEscaped() {
            assertThat(ReportQueryBuilder.escapeLike("100%")).isEqualTo("100\\%");
            assertThat(ReportQueryBuilder.escapeLike("a_b")).isEqualTo("a\\_b");
            assertThat(ReportQueryBuilder.escapeLike("back\\slash")).isEqualTo("back\\\\slash");
        }

        @Test
        @DisplayName("ordinary text is left alone")
        void ordinaryTextIsUnchanged() {
            assertThat(ReportQueryBuilder.escapeLike("500010299071")).isEqualTo("500010299071");
        }

        @Test
        @DisplayName("an escaped wildcard is still followed by the prefix wildcard")
        void escapedValueStillGetsThePrefixWildcard() {
            ReportCriteria c = criteria();
            c.setGameId("100%");

            // The user's % is literal; the trailing % is ours.
            assertThat(builder.rows(c).getParameters()).containsEntry("gameId", "100\\%%");
        }
    }

    // -----------------------------------------------------------------------
    // Summary query
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("summary query")
    class Summary {

        @Test
        @DisplayName("count and all three totals come from a single statement")
        void countAndTotalsInOneStatement() {
            String sql = builder.summary(criteria()).getSql();

            assertThat(sql).startsWith("SELECT COUNT(*)");
            assertThat(sql).contains("t.TRAN_TYPE IN (:betTranTypes)");
            assertThat(sql).contains("t.TRAN_TYPE IN (:winTranTypes)");
            // One FROM, so the range is scanned once rather than twice.
            assertThat(sql.split("FROM", -1)).hasSize(2);
        }

        @Test
        @DisplayName("the summary applies exactly the same filters as the row query")
        void summaryUsesTheSameFilters() {
            ReportCriteria c = criteria();
            c.setAccountId(2203L);

            SqlQuery rows = builder.rows(c);
            SqlQuery summary = builder.summary(c);

            assertThat(summary.getSql()).contains("AND t.ACCOUNT_ID = :accountId");
            assertThat(summary.getParameters())
                    .containsEntry("accountId", rows.getParameters().get("accountId"));
        }

        @Test
        @DisplayName("totals are coalesced so an empty range returns zero, not null")
        void emptyRangeYieldsZeroNotNull() {
            assertThat(builder.summary(criteria()).getSql()).contains("COALESCE(SUM(");
        }

        @Test
        @DisplayName("with no configured bet types the predicate is false rather than invalid SQL")
        void noConfiguredTypesGivesAFalsePredicate() {
            ReportQueryBuilder noTypes = new ReportQueryBuilder(
                    "account_tran", AMOUNT, BALANCE,
                    Collections.emptyList(), Collections.emptyList());

            SqlQuery q = noTypes.summary(criteria());

            // "IN ()" is a syntax error, so an empty list becomes 1 = 0.
            assertThat(q.getSql()).contains("1 = 0");
            assertThat(q.getParameters()).doesNotContainKey("betTranTypes");
        }
    }

    // -----------------------------------------------------------------------
    // Projection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("the row query selects the nine report columns in the mapped order")
    void rowQuerySelectsTheReportColumns() {
        String sql = builder.rows(criteria()).getSql();

        assertThat(sql).startsWith("SELECT t.ID, t.ACCOUNT_ID, t.`DATETIME`, t.TRAN_TYPE,"
                + " t.PLATFORM_TRAN_ID, t.GAME_TRAN_ID, t.GAME_ID, "
                + AMOUNT + " AS AMOUNT_TOTAL, " + BALANCE + " AS BALANCE_TOTAL");
        assertThat(sql).contains("FROM account_tran t");
    }

    @Test
    @DisplayName("paging is left to the JPA layer, not baked into the SQL")
    void noLimitOrOffsetInTheGeneratedSql() {
        // setFirstResult/setMaxResults let Hibernate emit dialect-correct
        // syntax, which is why the same SQL also runs against H2.
        String sql = builder.rows(criteria()).getSql();

        assertThat(sql).doesNotContain("LIMIT").doesNotContain("OFFSET");
    }

    @Test
    @DisplayName("parameters are exposed as an unmodifiable map")
    void parametersCannotBeMutatedAfterConstruction() {
        Map<String, Object> params = builder.rows(criteria()).getParameters();

        assertThat(params).isUnmodifiable();
    }
}
