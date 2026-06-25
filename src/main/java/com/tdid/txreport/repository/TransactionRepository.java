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
                "SELECT COUNT(tranID) FROM transactions WHERE tranStatus = 'SUCCESS'");
        appendOrgFilter(sql, params, orgId);
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
                "FROM transactions WHERE tranStatus = 'SUCCESS'");
        appendOrgFilter(sql, params, orgId);
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

    /** Gateway IDs that have billable (SUCCESS, signing) transactions, busiest first — for the dropdown. */
    public List<String> findGatewayIdsWithData() {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT gatewayID FROM transactions WHERE tranStatus = 'SUCCESS' AND gatewayID <> ''");
        appendSigningFilter(sql, params);
        sql.append(" GROUP BY gatewayID ORDER BY COUNT(*) DESC");
        return jdbc.queryForList(sql.toString(), String.class, params.toArray());
    }

    /** All orgIDs (signing SUCCESS) routed through a gateway, busiest first (first = representative). */
    public List<String> findOrgIdsForGateway(String gatewayId) {
        List<Object> params = new ArrayList<>();
        params.add(gatewayId);
        StringBuilder sql = new StringBuilder(
                "SELECT orgID FROM transactions WHERE tranStatus = 'SUCCESS' AND gatewayID = ?");
        appendSigningFilter(sql, params);
        sql.append(" GROUP BY orgID ORDER BY COUNT(*) DESC");
        return jdbc.queryForList(sql.toString(), String.class, params.toArray());
    }

    /** Whether any transaction exists for this gateway (cheap existence check). */
    public boolean existsGateway(String gatewayId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transactions WHERE gatewayID = ?)", Boolean.class, gatewayId);
        return Boolean.TRUE.equals(exists);
    }

    /** Optional orgID filter (skipped when null/blank, e.g. gateway-scoped reports). */
    private void appendOrgFilter(StringBuilder sql, List<Object> params, String orgId) {
        if (orgId != null && !orgId.isBlank()) {
            sql.append(" AND orgID = ?");
            params.add(orgId);
        }
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

    /** The report is counted by gatewayID — applied whenever one is given. */
    private void appendGatewayFilter(StringBuilder sql, List<Object> params, String gatewayId) {
        if (gatewayId != null && !gatewayId.isBlank()) {
            sql.append(" AND gatewayID = ?");
            params.add(gatewayId);
        }
    }
}
