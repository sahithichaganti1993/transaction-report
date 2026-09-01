package com.bet99.report.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.bet99.report.domain.AccountTran;

/** Spring Data JPA repository over the mapped entity. */
@Repository
public interface AccountTranRepository extends JpaRepository<AccountTran, Long> {

    /** Populates the TRAN_TYPE filter's suggestion list. */
    @Query("SELECT DISTINCT a.tranType FROM AccountTran a ORDER BY a.tranType")
    List<String> findDistinctTranTypes();

    /**
     * Oldest and newest transaction as {@code [min, max]}, used to prefill the
     * search form so the first page anyone sees is not an empty date range.
     */
    @Query("SELECT MIN(a.datetime), MAX(a.datetime) FROM AccountTran a")
    List<Object[]> findDataWindow();
}
