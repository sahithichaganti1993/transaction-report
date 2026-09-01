package com.bet99.report.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.bet99.report.config.ReportProperties;

/**
 * Resolves which physical columns make up the report's {@code amount} and
 * {@code balance} values.
 *
 * <p>The assignment defines those two report columns as "the sum of all the
 * AMOUNT_* / BALANCE_* columns", which is a property of the schema, not of the
 * code. So rather than hard-coding a list that breaks the moment the data
 * script changes, this component reads INFORMATION_SCHEMA once at startup.
 * An explicit list in configuration always wins, which is what the tests use.
 */
@Component
public class TransactionColumnRegistry {

    private static final Logger log = LoggerFactory.getLogger(TransactionColumnRegistry.class);

    /** Guards against anything that is not a plain SQL identifier reaching the query text. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final List<String> amountColumns;
    private final List<String> balanceColumns;

    public TransactionColumnRegistry(DataSource dataSource, ReportProperties properties) {
        String table = requireSafeIdentifier(properties.getTable(), "report.table");

        List<String> configuredAmounts = properties.getAmountColumns();
        List<String> configuredBalances = properties.getBalanceColumns();

        Set<String> excluded = upperCased(properties.getExcludeColumns());

        if (!configuredAmounts.isEmpty() || !configuredBalances.isEmpty()) {
            this.amountColumns = validated(configuredAmounts, "report.amount-columns");
            this.balanceColumns = validated(configuredBalances, "report.balance-columns");
            log.info("Using configured money columns: amount={}, balance={}", amountColumns, balanceColumns);
        } else {
            List<String> all = discoverColumns(new JdbcTemplate(dataSource), table);
            this.amountColumns = withPrefix(all, properties.getAmountColumnPrefix(), excluded);
            this.balanceColumns = withPrefix(all, properties.getBalanceColumnPrefix(), excluded);
            log.info("Discovered money columns on {}: amount={}, balance={}{}", table, amountColumns, balanceColumns,
                    excluded.isEmpty() ? "" : " (excluded " + excluded + ")");
        }

        if (amountColumns.isEmpty() && balanceColumns.isEmpty()) {
            // Fail fast: a report with no money columns is not worth serving.
            // Log the remediation as a plain line first - buried in a Spring
            // bean-creation stack trace it is easy to miss, and the usual cause
            // (the SQL scripts never ran) has a one-command fix.
            String message = "No AMOUNT_*/BALANCE_* columns found on table '" + table + "' in the "
                    + "configured database. Most likely the schema has not been created: run "
                    + "db/00_database.sql, db/01_account_tran.sql and db/02_indexes.sql, or "
                    + "`docker compose down -v && docker compose up --build` to rebuild the "
                    + "database image. If the table exists but uses different money-column "
                    + "names, set report.amount-columns / report.balance-columns explicitly.";
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    public List<String> getAmountColumns() {
        return amountColumns;
    }

    public List<String> getBalanceColumns() {
        return balanceColumns;
    }

    /**
     * Builds {@code (COALESCE(t.A,0) + COALESCE(t.B,0))} for the given columns,
     * or the literal {@code 0} when there are none.
     */
    public String sumExpression(String tableAlias, List<String> columns) {
        if (columns.isEmpty()) {
            return "0";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(" + ");
            }
            sb.append("COALESCE(").append(tableAlias).append(".`").append(columns.get(i)).append("`, 0)");
        }
        return sb.append(')').toString();
    }

    public String amountExpression(String tableAlias) {
        return sumExpression(tableAlias, amountColumns);
    }

    public String balanceExpression(String tableAlias) {
        return sumExpression(tableAlias, balanceColumns);
    }

    private List<String> discoverColumns(JdbcTemplate jdbc, String table) {
        List<String> columns = jdbc.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE() "
                        + "ORDER BY ORDINAL_POSITION", String.class, table);

        if (columns.isEmpty()) {
            // Fall back for setups where the connection's default schema is not
            // the one holding the table (e.g. a shared reporting user).
            columns = jdbc.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_NAME = ? ORDER BY ORDINAL_POSITION", String.class, table);
        }
        return columns;
    }

    private static List<String> withPrefix(List<String> columns, String prefix, Set<String> excluded) {
        String upperPrefix = prefix.toUpperCase(Locale.ROOT);
        List<String> matched = new ArrayList<>();
        for (String column : columns) {
            if (column == null || !SAFE_IDENTIFIER.matcher(column).matches()) {
                continue;
            }
            String upper = column.toUpperCase(Locale.ROOT);
            if (upper.startsWith(upperPrefix) && !excluded.contains(upper)) {
                matched.add(column);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    private static Set<String> upperCased(List<String> values) {
        Set<String> upper = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                upper.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        return upper;
    }

    private static List<String> validated(List<String> columns, String propertyName) {
        List<String> checked = new ArrayList<>(columns.size());
        for (String column : columns) {
            checked.add(requireSafeIdentifier(column, propertyName));
        }
        return Collections.unmodifiableList(checked);
    }

    private static String requireSafeIdentifier(String value, String propertyName) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(propertyName + " contains an illegal SQL identifier: " + value);
        }
        return value;
    }
}
