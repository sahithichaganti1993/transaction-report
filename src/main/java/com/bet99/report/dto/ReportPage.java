package com.bet99.report.dto;

import java.util.List;

/** One page of report rows plus the totals needed to render the page controls. */
public class ReportPage {

    private final List<TransactionRow> rows;
    private final ReportSummary summary;
    private final int page;
    private final int size;

    public ReportPage(List<TransactionRow> rows, ReportSummary summary, int page, int size) {
        this.rows = rows;
        this.summary = summary;
        this.page = page;
        this.size = size;
    }

    public List<TransactionRow> getRows() { return rows; }
    public ReportSummary getSummary() { return summary; }
    public int getPage() { return page; }
    public int getSize() { return size; }

    public long getTotalRows() { return summary.getTotalRows(); }

    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) ((summary.getTotalRows() + size - 1) / size);
    }

    public boolean isHasPrevious() { return page > 1; }

    public boolean isHasNext() { return page < getTotalPages(); }

    /** 1-based index of the first row on this page, or 0 when empty. */
    public long getFirstRowNumber() {
        return rows.isEmpty() ? 0 : (long) (page - 1) * size + 1;
    }

    public long getLastRowNumber() {
        return rows.isEmpty() ? 0 : getFirstRowNumber() + rows.size() - 1;
    }
}
