package com.bet99.report.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bet99.report.web.ReportCriteria;

/**
 * Turns a {@link ReportCriteria} into native SQL.
 *
 * <p>Deliberately a plain object with no Spring or JPA dependencies so the
 * generated SQL can be asserted directly in unit tests.
 *
 * <p>Injection safety: every value reaches the database as a named bind
 * parameter. The only pieces of SQL assembled from user input are the ORDER BY
 * column and direction, and both go through a fixed whitelist - an unknown sort
 * key silently falls back to the default rather than being interpolated.
 */
public class ReportQueryBuilder {

    public static final String DEFAULT_SORT = "datetime";
    public static final String ALIAS = "t";

    private final String table;
    private final String amountExpression;
    private final String balanceExpression;
    private final List<String> betTranTypes;
    private final List<String> winTranTypes;
    private final Map<String, String> sortableColumns;

    public ReportQueryBuilder(String table, String amountExpression, String balanceExpression,
                              List<String> betTranTypes, List<String> winTranTypes) {
        this.table = table;
        this.amountExpression = amountExpression;
        this.balanceExpression = balanceExpression;
        this.betTranTypes = Collections.unmodifiableList(new ArrayList<>(betTranTypes));
        this.winTranTypes = Collections.unmodifiableList(new ArrayList<>(winTranTypes));

        Map<String, String> sortable = new LinkedHashMap<>();
        sortable.put("id", ALIAS + ".ID");
        sortable.put("accountId", ALIAS + ".ACCOUNT_ID");
        sortable.put("datetime", ALIAS + ".`DATETIME`");
        sortable.put("tranType", ALIAS + ".TRAN_TYPE");
        sortable.put("platformTranId", ALIAS + ".PLATFORM_TRAN_ID");
        sortable.put("gameTranId", ALIAS + ".GAME_TRAN_ID");
        sortable.put("gameId", ALIAS + ".GAME_ID");
        sortable.put("amount", amountExpression);
        sortable.put("balance", balanceExpression);
        this.sortableColumns = Collections.unmodifiableMap(sortable);
    }

    public Set<String> sortableKeys() {
        return sortableColumns.keySet();
    }

    public boolean isSortable(String key) {
        return key != null && sortableColumns.containsKey(key);
    }

    /** Unknown keys fall back to the default rather than reaching the SQL text. */
    public String normalizeSort(String key) {
        return isSortable(key) ? key : DEFAULT_SORT;
    }

    /** The paged row query. LIMIT/OFFSET are applied by the JPA query, not here. */
    public SqlQuery rows(ReportCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(criteria, params);

        String sql = "SELECT " + ALIAS + ".ID"
                + ", " + ALIAS + ".ACCOUNT_ID"
                + ", " + ALIAS + ".`DATETIME`"
                + ", " + ALIAS + ".TRAN_TYPE"
                + ", " + ALIAS + ".PLATFORM_TRAN_ID"
                + ", " + ALIAS + ".GAME_TRAN_ID"
                + ", " + ALIAS + ".GAME_ID"
                + ", " + amountExpression + " AS AMOUNT_TOTAL"
                + ", " + balanceExpression + " AS BALANCE_TOTAL"
                + " FROM " + table + " " + ALIAS
                + where
                + orderByClause(criteria);

        return new SqlQuery(sql, params);
    }

    /**
     * Total count and the bet/win/amount totals in a single round trip - the
     * WHERE clause is identical to {@link #rows}, so running them separately
     * would scan the same range twice.
     *
     * <p>The bet and win totals are returned with whatever sign the data uses;
     * turning them into the magnitudes the summary section displays is
     * {@link com.bet99.report.dto.ReportSummary}'s job.
     */
    public SqlQuery summary(ReportCriteria criteria) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = whereClause(criteria, params);

        String betPredicate = tranTypePredicate(betTranTypes, "betTranTypes", params);
        String winPredicate = tranTypePredicate(winTranTypes, "winTranTypes", params);

        String sql = "SELECT COUNT(*)"
                + ", COALESCE(SUM(CASE WHEN " + betPredicate + " THEN "
                + amountExpression + " ELSE 0 END), 0)"
                + ", COALESCE(SUM(CASE WHEN " + winPredicate + " THEN "
                + amountExpression + " ELSE 0 END), 0)"
                + ", COALESCE(SUM(" + amountExpression + "), 0)"
                + " FROM " + table + " " + ALIAS
                + where;

        return new SqlQuery(sql, params);
    }

    /** {@code FALSE} when no types are configured, so the total is simply zero. */
    private String tranTypePredicate(List<String> tranTypes, String param, Map<String, Object> params) {
        if (tranTypes.isEmpty()) {
            return "1 = 0";
        }
        params.put(param, tranTypes);
        return ALIAS + ".TRAN_TYPE IN (:" + param + ")";
    }

    private String whereClause(ReportCriteria c, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder(" WHERE " + ALIAS + ".`DATETIME` >= :startDateTime"
                + " AND " + ALIAS + ".`DATETIME` <= :endDateTime");
        params.put("startDateTime", c.getStartDateTime());
        params.put("endDateTime", c.getEndDateTime());

        if (c.getAccountId() != null) {
            sb.append(" AND ").append(ALIAS).append(".ACCOUNT_ID = :accountId");
            params.put("accountId", c.getAccountId());
        }
        // Prefix matching keeps these predicates index-friendly; a leading
        // wildcard would force a full scan of the date range.
        addPrefixFilter(sb, params, "PLATFORM_TRAN_ID", "platformTranId", c.getPlatformTranId());
        addPrefixFilter(sb, params, "GAME_TRAN_ID", "gameTranId", c.getGameTranId());
        addPrefixFilter(sb, params, "GAME_ID", "gameId", c.getGameId());

        if (hasText(c.getTranType())) {
            sb.append(" AND ").append(ALIAS).append(".TRAN_TYPE = :tranType");
            params.put("tranType", c.getTranType().trim());
        }
        return sb.toString();
    }

    private void addPrefixFilter(StringBuilder sb, Map<String, Object> params,
                                 String column, String param, String value) {
        if (!hasText(value)) {
            return;
        }
        sb.append(" AND ").append(ALIAS).append('.').append(column).append(" LIKE :").append(param);
        params.put(param, escapeLike(value.trim()) + "%");
    }

    private String orderByClause(ReportCriteria criteria) {
        String key = normalizeSort(criteria.getSort());
        String column = sortableColumns.get(key);
        String direction = criteria.isDescending() ? "DESC" : "ASC";

        // ID breaks ties so paging stays stable across requests.
        String tieBreaker = "id".equals(key) ? "" : ", " + ALIAS + ".ID " + direction;
        return " ORDER BY " + column + " " + direction + tieBreaker;
    }

    /** Stops user input from turning into LIKE wildcards. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getAmountExpression() { return amountExpression; }

    public String getBalanceExpression() { return balanceExpression; }

    public List<String> getBetTranTypes() { return betTranTypes; }

    public List<String> getWinTranTypes() { return winTranTypes; }
}
