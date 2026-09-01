package com.bet99.report.dto;

import java.math.BigDecimal;

/**
 * Totals for the whole filtered result set, not just the visible page.
 *
 * <p><strong>Sign convention.</strong> In the supplied dataset a wager is a
 * debit: {@code GAME_BET} rows carry a negative {@code AMOUNT_REAL} (for
 * example {@code -7.00}), while {@code GAME_WIN} rows are positive. Summing
 * those raw values and subtracting would give
 * {@code 17,525.36 - (-50,062.69) = +67,588.05}, which is meaningless.
 *
 * <p>So the bet total is reported as a <em>magnitude</em> - how much was
 * staked - and {@code net = win - bet} then reads the way the assignment
 * intends: negative when players lost more than they won. Taking the absolute
 * value also makes this correct for the opposite convention, where wagers are
 * stored as positive numbers: there it is simply a no-op.
 */
public class ReportSummary {

    public static final ReportSummary EMPTY =
            new ReportSummary(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    private final long totalRows;
    private final BigDecimal betSumRaw;
    private final BigDecimal winSumRaw;
    private final BigDecimal amountSum;

    public ReportSummary(long totalRows, BigDecimal betSumRaw, BigDecimal winSumRaw, BigDecimal amountSum) {
        this.totalRows = totalRows;
        this.betSumRaw = betSumRaw == null ? BigDecimal.ZERO : betSumRaw;
        this.winSumRaw = winSumRaw == null ? BigDecimal.ZERO : winSumRaw;
        this.amountSum = amountSum == null ? BigDecimal.ZERO : amountSum;
    }

    public long getTotalRows() { return totalRows; }

    /** Total staked, as a positive magnitude. */
    public BigDecimal getBetSum() { return betSumRaw.abs(); }

    /** Total paid out, as a positive magnitude. */
    public BigDecimal getWinSum() { return winSumRaw.abs(); }

    /** Signed sum of every row's amount, exactly as stored. */
    public BigDecimal getAmountSum() { return amountSum; }

    /** As stored, before the magnitude conversion - useful when debugging. */
    public BigDecimal getBetSumRaw() { return betSumRaw; }

    /** As stored, before the magnitude conversion - useful when debugging. */
    public BigDecimal getWinSumRaw() { return winSumRaw; }

    /** net = win - bet, per the assignment's summary bonus. */
    public BigDecimal getNet() {
        return getWinSum().subtract(getBetSum());
    }
}
