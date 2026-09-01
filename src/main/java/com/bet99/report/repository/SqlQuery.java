package com.bet99.report.repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A SQL string paired with the named parameters it expects */
public class SqlQuery {

    private final String sql;
    private final Map<String, Object> parameters;

    public SqlQuery(String sql, Map<String, Object> parameters) {
        this.sql = sql;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public String getSql() { return sql; }

    public Map<String, Object> getParameters() { return parameters; }

    @Override
    public String toString() {
        return sql + " " + parameters;
    }
}
