package com.bet99.report.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_tran")
public class AccountTran {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Integer accountId;

    // Backticks because DATETIME is a reserved SQL word.
    @Column(name = "`DATETIME`", nullable = false)
    private LocalDateTime datetime;

    @Column(name = "TRAN_TYPE", nullable = false)
    private String tranType;

    @Column(name = "PLATFORM_TRAN_ID", length = 100)
    private String platformTranId;

    @Column(name = "GAME_TRAN_ID", length = 100)
    private String gameTranId;

    @Column(name = "GAME_ID", length = 100)
    private String gameId;

    @Column(name = "PLATFORM_ID")
    private Integer platformId;

    @Column(name = "ROLLED_BACK")
    private Integer rolledBack;

    @Column(name = "GAME_INSTANCE_ID")
    private Long gameInstanceId;

    @Column(name = "CHANNEL", length = 25)
    private String channel;

    @Column(name = "LOGDATETIME", nullable = false)
    private LocalDateTime logDatetime;

    // --- AMOUNT_* -----------------------------------------------------------

    @Column(name = "AMOUNT_REAL")
    private BigDecimal amountReal;

    @Column(name = "AMOUNT_RELEASED_BONUS")
    private BigDecimal amountReleasedBonus;

    @Column(name = "AMOUNT_PLAYABLE_BONUS")
    private BigDecimal amountPlayableBonus;

    @Column(name = "AMOUNT_UNDERFLOW")
    private BigDecimal amountUnderflow;

    /** Loyalty points, not currency - stored as {@code BIGINT}. */
    @Column(name = "AMOUNT_RAW_LOYALTY")
    private Long amountRawLoyalty;

    @Column(name = "AMOUNT_FREE_BET")
    private BigDecimal amountFreeBet;

    // --- BALANCE_* ----------------------------------------------------------

    @Column(name = "BALANCE_REAL")
    private BigDecimal balanceReal;

    @Column(name = "BALANCE_RELEASED_BONUS")
    private BigDecimal balanceReleasedBonus;

    @Column(name = "BALANCE_PLAYABLE_BONUS")
    private BigDecimal balancePlayableBonus;

    /** Loyalty points, not currency - stored as {@code BIGINT}. */
    @Column(name = "BALANCE_RAW_LOYALTY")
    private Long balanceRawLoyalty;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }

    public LocalDateTime getDatetime() { return datetime; }
    public void setDatetime(LocalDateTime datetime) { this.datetime = datetime; }

    public LocalDateTime getLogDatetime() { return logDatetime; }
    public void setLogDatetime(LocalDateTime logDatetime) { this.logDatetime = logDatetime; }

    public String getTranType() { return tranType; }
    public void setTranType(String tranType) { this.tranType = tranType; }

    public String getPlatformTranId() { return platformTranId; }
    public void setPlatformTranId(String platformTranId) { this.platformTranId = platformTranId; }

    public String getGameTranId() { return gameTranId; }
    public void setGameTranId(String gameTranId) { this.gameTranId = gameTranId; }

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public Integer getPlatformId() { return platformId; }
    public void setPlatformId(Integer platformId) { this.platformId = platformId; }

    public Integer getRolledBack() { return rolledBack; }
    public void setRolledBack(Integer rolledBack) { this.rolledBack = rolledBack; }

    public Long getGameInstanceId() { return gameInstanceId; }
    public void setGameInstanceId(Long gameInstanceId) { this.gameInstanceId = gameInstanceId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public BigDecimal getAmountReal() { return amountReal; }
    public void setAmountReal(BigDecimal v) { this.amountReal = v; }

    public BigDecimal getAmountReleasedBonus() { return amountReleasedBonus; }
    public void setAmountReleasedBonus(BigDecimal v) { this.amountReleasedBonus = v; }

    public BigDecimal getAmountPlayableBonus() { return amountPlayableBonus; }
    public void setAmountPlayableBonus(BigDecimal v) { this.amountPlayableBonus = v; }

    public BigDecimal getAmountUnderflow() { return amountUnderflow; }
    public void setAmountUnderflow(BigDecimal v) { this.amountUnderflow = v; }

    public Long getAmountRawLoyalty() { return amountRawLoyalty; }
    public void setAmountRawLoyalty(Long v) { this.amountRawLoyalty = v; }

    public BigDecimal getAmountFreeBet() { return amountFreeBet; }
    public void setAmountFreeBet(BigDecimal v) { this.amountFreeBet = v; }

    public BigDecimal getBalanceReal() { return balanceReal; }
    public void setBalanceReal(BigDecimal v) { this.balanceReal = v; }

    public BigDecimal getBalanceReleasedBonus() { return balanceReleasedBonus; }
    public void setBalanceReleasedBonus(BigDecimal v) { this.balanceReleasedBonus = v; }

    public BigDecimal getBalancePlayableBonus() { return balancePlayableBonus; }
    public void setBalancePlayableBonus(BigDecimal v) { this.balancePlayableBonus = v; }

    public Long getBalanceRawLoyalty() { return balanceRawLoyalty; }
    public void setBalanceRawLoyalty(Long v) { this.balanceRawLoyalty = v; }

}