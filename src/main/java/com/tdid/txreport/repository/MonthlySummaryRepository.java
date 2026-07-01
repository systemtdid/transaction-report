package com.tdid.txreport.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read/write access to {@code report_monthly_summary} — pre-aggregated SUCCESS+signing
 * transaction counts per (gateway, org, month). Lets monthly reports avoid scanning the
 * huge transactions table at 4-10M rows/month. See ARCHITECTURE.md.
 */
@Repository
public class MonthlySummaryRepository {

    private final JdbcTemplate jdbc;
    private final List<Integer> signingFunctionIds;

    public MonthlySummaryRepository(JdbcTemplate jdbc,
                                    @Value("${txreport.signing-function-ids:1,4,14}") List<Integer> signingFunctionIds) {
        this.jdbc = jdbc;
        this.signingFunctionIds = signingFunctionIds;
    }

    public boolean tableExists() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = 'report_monthly_summary'",
                Long.class);
        return n != null && n > 0;
    }

    /** Best-effort create (only succeeds if the DB user has CREATE). Returns whether it exists after. */
    public boolean createTableIfMissing() {
        if (tableExists()) {
            return true;
        }
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS report_monthly_summary (" +
                "gateway_id VARCHAR(50) NOT NULL, org_id VARCHAR(35) NOT NULL, " +
                "year_month CHAR(7) NOT NULL, success_count BIGINT NOT NULL DEFAULT 0, " +
                "updated_at DATETIME NOT NULL, PRIMARY KEY (gateway_id, year_month, org_id)) " +
                "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (RuntimeException e) {
            return false;
        }
        return tableExists();
    }

    /** Re-aggregate one month from transactions into the summary (idempotent upsert). */
    public void refreshMonth(String yearMonth, LocalDateTime start, LocalDateTime end) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "INSERT INTO report_monthly_summary (gateway_id, org_id, year_month, success_count, updated_at) " +
                "SELECT gatewayID, orgID, ?, COUNT(*), NOW() FROM transactions " +
                "WHERE tranStatus = 'SUCCESS' AND gatewayID <> ''");
        params.add(yearMonth);
        appendSigning(sql, params);
        sql.append(" AND processedTime >= ? AND processedTime < ? GROUP BY gatewayID, orgID ");
        params.add(start);
        params.add(end);
        sql.append("ON DUPLICATE KEY UPDATE success_count = VALUES(success_count), updated_at = NOW()");
        jdbc.update(sql.toString(), params.toArray());
    }

    /** Usage for one gateway/month = sum across the gateway's orgs. */
    public long usage(String gatewayId, String yearMonth) {
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(success_count), 0) FROM report_monthly_summary " +
                "WHERE gateway_id = ? AND year_month = ?",
                Long.class, gatewayId, yearMonth);
        return n != null ? n : 0L;
    }

    /** Accumulated for one gateway across a month range [fromYm, toYm] inclusive ('YYYY-MM' sorts chronologically). */
    public long accumulated(String gatewayId, String fromYm, String toYm) {
        Long n = jdbc.queryForObject(
                "SELECT COALESCE(SUM(success_count), 0) FROM report_monthly_summary " +
                "WHERE gateway_id = ? AND year_month BETWEEN ? AND ?",
                Long.class, gatewayId, fromYm, toYm);
        return n != null ? n : 0L;
    }

    private void appendSigning(StringBuilder sql, List<Object> params) {
        if (signingFunctionIds == null || signingFunctionIds.isEmpty()) {
            return;
        }
        String ph = signingFunctionIds.stream().map(x -> "?").collect(Collectors.joining(", "));
        sql.append(" AND functionID IN (").append(ph).append(")");
        params.addAll(signingFunctionIds);
    }
}
