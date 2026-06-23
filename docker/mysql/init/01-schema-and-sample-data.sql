-- =========================================================================
-- Transaction Report — local development schema + sample data
--
-- Runs automatically ONCE when the MySQL data volume is first initialized.
-- This is SAMPLE data for local development, NOT production data. To point
-- the app at a real fmsv_db, change DB_HOST/DB_USER/DB_PASSWORD instead.
-- =========================================================================

CREATE DATABASE IF NOT EXISTS fmsv_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fmsv_db;

-- Inferred schema (see ARCHITECTURE.md §2.3) ------------------------------
CREATE TABLE IF NOT EXISTS transactions (
    tranID        BIGINT       NOT NULL AUTO_INCREMENT,
    orgID         VARCHAR(32)  NOT NULL,
    tranStatus    VARCHAR(16)  NOT NULL,
    processedTime DATETIME     NOT NULL,
    gatewayID     VARCHAR(32)  DEFAULT NULL,
    PRIMARY KEY (tranID),
    KEY idx_org_status_time (orgID, tranStatus, processedTime),
    KEY idx_gateway (gatewayID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Allow the recursive generators below to run -----------------------------
SET SESSION cte_max_recursion_depth = 1000000;

-- Shared generators: every day of 2026-01-01 .. 2026-06-30, and 1..600 ----
-- Counts vary per day so the reports look realistic.

-- GSB (Government Saving Bank) — daily report org, requires gatewayID filter
INSERT INTO transactions (orgID, tranStatus, processedTime, gatewayID)
WITH RECURSIVE
  days AS (SELECT DATE('2026-01-01') AS d
           UNION ALL SELECT d + INTERVAL 1 DAY FROM days WHERE d < '2026-06-30'),
  nums AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM nums WHERE n < 600)
SELECT '0994000164891', 'SUCCESS',
       TIMESTAMP(d) + INTERVAL ((n * 131) MOD 86400) SECOND,
       'TIPxGSB01'
FROM days JOIN nums
  ON nums.n <= 40 + ((DAY(d) * 7 + MONTH(d) * 3) MOD 160);

-- DHIP — monthly billing org (has fee schedule + minimum-commitment rule)
INSERT INTO transactions (orgID, tranStatus, processedTime, gatewayID)
WITH RECURSIVE
  days AS (SELECT DATE('2026-01-01') AS d
           UNION ALL SELECT d + INTERVAL 1 DAY FROM days WHERE d < '2026-06-30'),
  nums AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM nums WHERE n < 600)
SELECT '0107556000051', 'SUCCESS',
       TIMESTAMP(d) + INTERVAL ((n * 97) MOD 86400) SECOND,
       NULL
FROM days JOIN nums
  ON nums.n <= 200 + ((DAY(d) * 11) MOD 300);

-- KTB (Krung Thai Bank)
INSERT INTO transactions (orgID, tranStatus, processedTime, gatewayID)
WITH RECURSIVE
  days AS (SELECT DATE('2026-01-01') AS d
           UNION ALL SELECT d + INTERVAL 1 DAY FROM days WHERE d < '2026-06-30'),
  nums AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM nums WHERE n < 300)
SELECT '0107537000882', 'SUCCESS',
       TIMESTAMP(d) + INTERVAL ((n * 53) MOD 86400) SECOND,
       NULL
FROM days JOIN nums
  ON nums.n <= 100 + ((DAY(d) * 5) MOD 120);

-- TMC
INSERT INTO transactions (orgID, tranStatus, processedTime, gatewayID)
WITH RECURSIVE
  days AS (SELECT DATE('2026-01-01') AS d
           UNION ALL SELECT d + INTERVAL 1 DAY FROM days WHERE d < '2026-06-30'),
  nums AS (SELECT 1 AS n UNION ALL SELECT n + 1 FROM nums WHERE n < 300)
SELECT '0994000953534', 'SUCCESS',
       TIMESTAMP(d) + INTERVAL ((n * 71) MOD 86400) SECOND,
       NULL
FROM days JOIN nums
  ON nums.n <= 80 + ((DAY(d) * 3) MOD 90);

-- A handful of NON-success / wrong-gateway rows to prove the filters work
-- (these must NOT be counted by any report).
INSERT INTO transactions (orgID, tranStatus, processedTime, gatewayID) VALUES
  ('0994000164891', 'FAILED',  '2026-06-10 10:00:00', 'TIPxGSB01'),
  ('0994000164891', 'SUCCESS', '2026-06-10 10:05:00', 'OTHER_GW'),
  ('0107556000051', 'PENDING', '2026-05-15 12:00:00', NULL);
