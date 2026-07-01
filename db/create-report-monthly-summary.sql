-- =====================================================================
-- Monthly report summary table for transaction-report.
-- Pre-aggregates SUCCESS + signing (functionID 1,4,14) counts per
-- (gateway, org, month) so monthly reports don't scan the huge
-- transactions table at 4-10M rows/month.
--
--   Run with:  sudo mariadb fmsv_db < "create-summary-table.sql"
--
-- Idempotent: safe to re-run. Grants write access to the app user and
-- back-fills all historical months on first run.
-- =====================================================================

CREATE TABLE IF NOT EXISTS report_monthly_summary (
    gateway_id    VARCHAR(50)  NOT NULL,
    org_id        VARCHAR(35)  NOT NULL,
    year_month    CHAR(7)      NOT NULL,          -- 'YYYY-MM'
    success_count BIGINT       NOT NULL DEFAULT 0,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (gateway_id, year_month, org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- App connects as reporting_ro (read-only on everything else); it only needs
-- to read + write THIS summary table.
GRANT SELECT, INSERT, UPDATE, DELETE ON fmsv_db.report_monthly_summary TO 'reporting_ro'@'localhost';
FLUSH PRIVILEGES;

-- One-time back-fill of ALL historical months (heavy on first run at scale).
INSERT INTO report_monthly_summary (gateway_id, org_id, year_month, success_count, updated_at)
SELECT gatewayID, orgID, DATE_FORMAT(processedTime, '%Y-%m'), COUNT(*), NOW()
FROM transactions
WHERE tranStatus = 'SUCCESS' AND functionID IN (1,4,14) AND gatewayID <> ''
GROUP BY gatewayID, orgID, DATE_FORMAT(processedTime, '%Y-%m')
ON DUPLICATE KEY UPDATE success_count = VALUES(success_count), updated_at = NOW();

SELECT CONCAT('report_monthly_summary rows: ', COUNT(*)) AS '' FROM report_monthly_summary;
