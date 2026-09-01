package com.bet99.report.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Everything about the report that a new environment might need to change,
 * in one place. See {@code application.yml} for the defaults.
 */
@ConfigurationProperties(prefix = "report")
public class ReportProperties {

    /** Physical table holding the transactions. */
    private String table = "account_tran";

    /**
     * Columns summed into the report's {@code amount} column. Leave empty to
     * auto-discover every {@code AMOUNT_*} column from INFORMATION_SCHEMA.
     */
    private List<String> amountColumns = new ArrayList<>();

    /**
     * Columns summed into the report's {@code balance} column. Leave empty to
     * auto-discover every {@code BALANCE_*} column from INFORMATION_SCHEMA.
     */
    private List<String> balanceColumns = new ArrayList<>();

    /** Prefix used for auto-discovery of amount columns. */
    private String amountColumnPrefix = "AMOUNT_";

    /** Prefix used for auto-discovery of balance columns. */
    private String balanceColumnPrefix = "BALANCE_";

    /**
     * Columns that auto-discovery must skip even though they match a prefix.
     * Useful for non-currency columns such as {@code AMOUNT_RAW_LOYALTY} /
     * {@code BALANCE_RAW_LOYALTY}, which hold loyalty points rather than money.
     * Empty by default, so the report sums every matching column exactly as the
     * specification describes.
     */
    private List<String> excludeColumns = new ArrayList<>();

    /** TRAN_TYPE values that count as wagers in the summary section. */
    private List<String> betTranTypes = new ArrayList<>(Arrays.asList("GAME_BET"));

    /** TRAN_TYPE values that count as payouts in the summary section. */
    private List<String> winTranTypes = new ArrayList<>(Arrays.asList("GAME_WIN"));

    /** Page sizes offered in the UI. The first entry is the default. */
    private List<Integer> pageSizes = new ArrayList<>(Arrays.asList(25, 50, 100));

    /** Fallback range, in days, when the table's own date window is unavailable. */
    private int defaultRangeDays = 7;

    /** Hard cap on rows written to a CSV export. */
    private int csvMaxRows = 200_000;

    /** Rows fetched per round trip while streaming a CSV export. */
    private int csvChunkSize = 5_000;

    public int defaultPageSize() {
        return pageSizes.isEmpty() ? 25 : pageSizes.get(0);
    }

    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }

    public List<String> getAmountColumns() { return amountColumns; }
    public void setAmountColumns(List<String> amountColumns) { this.amountColumns = amountColumns; }

    public List<String> getBalanceColumns() { return balanceColumns; }
    public void setBalanceColumns(List<String> balanceColumns) { this.balanceColumns = balanceColumns; }

    public String getAmountColumnPrefix() { return amountColumnPrefix; }
    public void setAmountColumnPrefix(String p) { this.amountColumnPrefix = p; }

    public String getBalanceColumnPrefix() { return balanceColumnPrefix; }
    public void setBalanceColumnPrefix(String p) { this.balanceColumnPrefix = p; }

    public List<String> getExcludeColumns() { return excludeColumns; }
    public void setExcludeColumns(List<String> excludeColumns) { this.excludeColumns = excludeColumns; }

    public List<String> getBetTranTypes() { return betTranTypes; }
    public void setBetTranTypes(List<String> betTranTypes) { this.betTranTypes = betTranTypes; }

    public List<String> getWinTranTypes() { return winTranTypes; }
    public void setWinTranTypes(List<String> winTranTypes) { this.winTranTypes = winTranTypes; }

    public List<Integer> getPageSizes() { return pageSizes; }
    public void setPageSizes(List<Integer> pageSizes) { this.pageSizes = pageSizes; }

    public int getDefaultRangeDays() { return defaultRangeDays; }
    public void setDefaultRangeDays(int defaultRangeDays) { this.defaultRangeDays = defaultRangeDays; }

    public int getCsvMaxRows() { return csvMaxRows; }
    public void setCsvMaxRows(int csvMaxRows) { this.csvMaxRows = csvMaxRows; }

    public int getCsvChunkSize() { return csvChunkSize; }
    public void setCsvChunkSize(int csvChunkSize) { this.csvChunkSize = csvChunkSize; }
}
