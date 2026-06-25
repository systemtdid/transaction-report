package com.tdid.txreport.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tdid.txreport.domain.DailyCount;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbc;

    /** Signing function IDs to count: PDFsign=1, XMLsign=4, JsonSign=14 (configurable). */
    private final List<Integer> signingFunctionIds;

    public TransactionRepository(JdbcTemplate jdbc,
                                 @Value("${txreport.signing-function-ids:1,4,14}") List<Integer> signingFunctionIds) {
        this.jdbc = jdbc;
        this.signingFunctionIds = signingFunctionIds;
    }

    public long countSuccess(String orgId, String gatewayId,
                             LocalDateTime startInclusive, LocalDateTime endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(tranID) FROM transactions " +
                "WHERE orgID = ? AND tranStatus = 'SUCCESS'");
        params.add(orgId);
        appendSigningFilter(sql, params);
        sql.append(" AND processedTime >= ? AND processedTime < ?");
        params.add(startInclusive);
        params.add(endExclusive);
        appendGatewayFilter(sql, params, gatewayId);

        Long result = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return result != null ? result : 0L;
    }

    public List<DailyCount> countSuccessByDay(String orgId, String gatewayId,
                                               LocalDate monthStart, LocalDate endExclusive) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT DATE(processedTime) AS d, COUNT(tranID) AS c " +
                "FROM transactions " +
                "WHERE orgID = ? AND tranStatus = 'SUCCESS'");
        params.add(orgId);
        appendSigningFilter(sql, params);
        sql.append(" AND processedTime >= ? AND processedTime < ?");
        params.add(monthStart.atStartOfDay());
        params.add(endExclusive.atStartOfDay());
        appendGatewayFilter(sql, params, gatewayId);

        sql.append(" GROUP BY DATE(processedTime) ORDER BY d");

        return jdbc.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            LocalDate date = rs.getDate("d").toLocalDate();
            long count = rs.getLong("c");
            return new DailyCount(date, count);
        });
    }

    /** Restrict to signing transactions (PDFsign/XMLsign/JsonSign) per configured function IDs. */
    private void appendSigningFilter(StringBuilder sql, List<Object> params) {
        if (signingFunctionIds == null || signingFunctionIds.isEmpty()) {
            return;
        }
        String placeholders = signingFunctionIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(", "));
        sql.append(" AND functionID IN (").append(placeholders).append(")");
        params.addAll(signingFunctionIds);
    }

    /** The report is counted by gatewayID — applied whenever the org has one configured. */
    private void appendGatewayFilter(StringBuilder sql, List<Object> params, String gatewayId) {
        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gatewayID = ?");
            params.add(gatewayId);
        }
    }
}
