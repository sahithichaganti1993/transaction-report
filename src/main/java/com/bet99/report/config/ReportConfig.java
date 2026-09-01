package com.bet99.report.config;

import com.bet99.report.repository.ReportQueryBuilder;
import com.bet99.report.repository.TransactionColumnRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportConfig {

    /**
     * The query builder is fixed once the money columns are known, so it is
     * built once at startup instead of per request.
     */
    @Bean
    public ReportQueryBuilder reportQueryBuilder(TransactionColumnRegistry registry,
                                                 ReportProperties properties) {
        return new ReportQueryBuilder(
                properties.getTable(),
                registry.amountExpression(ReportQueryBuilder.ALIAS),
                registry.balanceExpression(ReportQueryBuilder.ALIAS),
                properties.getBetTranTypes(),
                properties.getWinTranTypes());
    }
}
