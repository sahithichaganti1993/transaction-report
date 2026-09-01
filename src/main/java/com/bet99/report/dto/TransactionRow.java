package com.bet99.report.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One row of the report. A plain bean (not a record) because JSTL/EL needs
 * {@code getX()} accessors.
 */
public class TransactionRow {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long id;
    private final long accountId;
    private final LocalDateTime datetime;
    private final String tranType;
    private final String platformTranId;
    private final String gameTranId;
    private final String gameId;
    private final BigDecimal amount;
    private final BigDecimal balance;

    public TransactionRow(long id, long accountId, LocalDateTime datetime, String tranType,
                          String platformTranId, String gameTranId, String gameId,
                          BigDecimal amount, BigDecimal balance) {
        this.id = id;
        this.accountId = accountId;
        this.datetime = datetime;
        this.tranType = tranType;
        this.platformTranId = platformTranId;
        this.gameTranId = gameTranId;
        this.gameId = gameId;
        this.amount = amount;
        this.balance = balance;
    }

    public long getId() { return id; }
    public long getAccountId() { return accountId; }
    public LocalDateTime getDatetime() { return datetime; }
    public String getTranType() { return tranType; }
    public String getPlatformTranId() { return platformTranId; }
    public String getGameTranId() { return gameTranId; }
    public String getGameId() { return gameId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalance() { return balance; }

    /** Pre-formatted for the JSP - {@code fmt:formatDate} cannot take a LocalDateTime. */
    public String getDatetimeText() {
        return datetime == null ? "" : DISPLAY.format(datetime);
    }
}
