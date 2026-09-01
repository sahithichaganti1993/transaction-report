package com.bet99.report.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.bet99.report.dto.ReportSummary;
import com.bet99.report.dto.TransactionRow;
import com.bet99.report.web.ReportCriteria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

/**
 * Reads the report through JPA's native-query API (so it still runs inside the
 * Hibernate session and transaction) rather than through the entity graph.
 *
 * <p>Why not JPQL / Criteria over {@link com.bet99.report.domain.AccountTran}?
 * The report's {@code amount} and {@code balance} are "the sum of every
 * AMOUNT_* / BALANCE_* column", a set that is known only at runtime. A static
 * mapping cannot express it, and sorting by those derived values has to happen
 * in the database - pulling 50k rows into the JVM to sort them is not an
 * option. So the projection is built by {@link ReportQueryBuilder}, and
 * filtering, sorting and paging all push down to MySQL.
 */
@Repository
public class TransactionReportRepository {

    // Column positions in the SELECT list built by ReportQueryBuilder.rows().
    private static final int C_ID = 0;
    private static final int C_ACCOUNT_ID = 1;
    private static final int C_DATETIME = 2;
    private static final int C_TRAN_TYPE = 3;
    private static final int C_PLATFORM_TRAN_ID = 4;
    private static final int C_GAME_TRAN_ID = 5;
    private static final int C_GAME_ID = 6;
    private static final int C_AMOUNT = 7;
    private static final int C_BALANCE = 8;

    @PersistenceContext
    private EntityManager entityManager;

    private final ReportQueryBuilder queryBuilder;

    public TransactionReportRepository(ReportQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    /** Visible for tests that construct the repository directly. */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<TransactionRow> findRows(ReportCriteria criteria, int offset, int limit) {
        SqlQuery sqlQuery = queryBuilder.rows(criteria);
        Query query = bind(entityManager.createNativeQuery(sqlQuery.getSql()), sqlQuery);
        query.setFirstResult(Math.max(offset, 0));
        query.setMaxResults(Math.max(limit, 0));

        @SuppressWarnings("unchecked")
        List<Object[]> raw = query.getResultList();

        List<TransactionRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            rows.add(new TransactionRow(
                    toLong(r[C_ID]),
                    toLong(r[C_ACCOUNT_ID]),
                    toLocalDateTime(r[C_DATETIME]),
                    toStr(r[C_TRAN_TYPE]),
                    toStr(r[C_PLATFORM_TRAN_ID]),
                    toStr(r[C_GAME_TRAN_ID]),
                    toStr(r[C_GAME_ID]),
                    toBigDecimal(r[C_AMOUNT]),
                    toBigDecimal(r[C_BALANCE])));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public ReportSummary findSummary(ReportCriteria criteria) {
        SqlQuery sqlQuery = queryBuilder.summary(criteria);
        Query query = bind(entityManager.createNativeQuery(sqlQuery.getSql()), sqlQuery);

        Object[] r = (Object[]) query.getSingleResult();
        return new ReportSummary(
                toLong(r[0]),
                toBigDecimal(r[1]),
                toBigDecimal(r[2]),
                toBigDecimal(r[3]));
    }

    private static Query bind(Query query, SqlQuery sqlQuery) {
        for (Map.Entry<String, Object> e : sqlQuery.getParameters().entrySet()) {
            query.setParameter(e.getKey(), e.getValue());
        }
        return query;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static String toStr(Object value) {
        return value == null ? null : value.toString();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
