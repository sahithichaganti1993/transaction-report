package com.bet99.report.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    /**
     * Query string carrying only the filter fields - sort, dir and page are
     * appended by whichever link is being rendered.
     */
    public String toFilterQueryString() {
        StringBuilder sb = new StringBuilder();
        append(sb, "startDateTime", startDateTime == null ? null : startDateTime.toString());
        append(sb, "endDateTime", endDateTime == null ? null : endDateTime.toString());
        append(sb, "accountId", accountId == null ? null : accountId.toString());
        append(sb, "platformTranId", platformTranId);
        append(sb, "gameTranId", gameTranId);
        append(sb, "gameId", gameId);
        append(sb, "tranType", tranType);
        append(sb, "size", Integer.toString(size));
        return sb.toString();
    }

    private static void append(StringBuilder sb, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(name).append('=').append(encode(value));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
