package com.bet99.report.web;

import java.time.LocalDateTime;

import com.bet99.report.web.validation.ValidDateRange;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;

/**
 * Form-backing object for the search form. Also carries the sort / paging state
 * so a single object round-trips through every link on the page.
 */
@ValidDateRange
public class ReportCriteria {

    @NotNull(message = "Start date/time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDateTime;

    @NotNull(message = "End date/time is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endDateTime;

    private Long accountId;

    private String platformTranId;
    private String gameTranId;
    private String gameId;
    private String tranType;

    private String sort = "datetime";
    private String dir = "desc";
    private int page = 1;
    private int size = 25;

    public LocalDateTime getStartDateTime() { return startDateTime; }
    public void setStartDateTime(LocalDateTime startDateTime) { this.startDateTime = startDateTime; }

    public LocalDateTime getEndDateTime() { return endDateTime; }
    public void setEndDateTime(LocalDateTime endDateTime) { this.endDateTime = endDateTime; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

    public String getPlatformTranId() { return platformTranId; }
    public void setPlatformTranId(String v) { this.platformTranId = trimToNull(v); }

    public String getGameTranId() { return gameTranId; }
    public void setGameTranId(String v) { this.gameTranId = trimToNull(v); }

    public String getGameId() { return gameId; }
    public void setGameId(String v) { this.gameId = trimToNull(v); }

    public String getTranType() { return tranType; }
    public void setTranType(String v) { this.tranType = trimToNull(v); }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public boolean isDescending() {
        return "desc".equalsIgnoreCase(dir);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
