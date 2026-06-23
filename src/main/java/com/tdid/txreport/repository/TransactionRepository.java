package com.tdid.txreport.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tdid.txreport.domain.DailyCount;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    public TransactionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countSuccess(String orgId, String gatewayId,
                             LocalDateTime startInclusive, LocalDateTime endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(tranID) FROM transactions " +
                "WHERE orgID = ? AND tranStatus = 'SUCCESS' " +
                "AND processedTime >= ? AND processedTime < ?");
        params.add(orgId);
        params.add(startInclusive);
        params.add(endExclusive);

        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gatewayID = ?");
            params.add(gatewayId);
        }

        Long result = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return result != null ? result : 0L;
    }

    public List<DailyCount> countSuccessByDay(String orgId, String gatewayId,
                                               LocalDate monthStart, LocalDate endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT DATE(processedTime) AS d, COUNT(tranID) AS c " +
                "FROM transactions " +
                "WHERE orgID = ? AND tranStatus = 'SUCCESS' " +
                "AND processedTime >= ? AND processedTime < ?");
        params.add(orgId);
        params.add(monthStart.atStartOfDay());
        params.add(endExclusive.atStartOfDay());

        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gatewayID = ?");
            params.add(gatewayId);
        }

        sql.append(" GROUP BY DATE(processedTime) ORDER BY d");

        return jdbc.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LocalDate date = rs.getDate("d").toLocalDate();
            long count = rs.getLong("c");
            return new DailyCount(date, count);
        });
    }
}
