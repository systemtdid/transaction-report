package com.tdid.txreport.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tdid.txreport.repository.MonthlySummaryRepository;

import jakarta.annotation.PostConstruct;

/**
 * Keeps {@code report_monthly_summary} in sync so monthly reports read a tiny table
 * instead of scanning transactions.
 * <ul>
 *   <li><b>Startup:</b> verify/create the table, then refresh recent months.</li>
 *   <li><b>Nightly:</b> refresh the current + previous month (past months are immutable,
 *       so they never need recomputing unless data arrives late).</li>
 * </ul>
 * If the table is missing/unwritable, monthly reports fall back to scanning transactions.
 */
@Component
public class SummaryMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(SummaryMaintenanceService.class);

    private final MonthlySummaryRepository summary;
    private final ZoneId zone;
    private volatile boolean available;

    public SummaryMaintenanceService(MonthlySummaryRepository summary,
                                     @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.summary = summary;
        this.zone = ZoneId.of(zoneName);
    }

    /** Whether monthly reports should read from the summary table (vs. fall back to transactions). */
    public boolean isAvailable() {
        return available;
    }

    @PostConstruct
    void init() {
        available = summary.createTableIfMissing();
        if (!available) {
            log.error("report_monthly_summary not found and could not be created. "
                    + "Run: sudo mariadb fmsv_db < Database_fmsv_db/create-summary-table.sql — "
                    + "monthly reports will FALL BACK to scanning the transactions table.");
            return;
        }
        try {
            refreshRecent();
            log.info("report_monthly_summary ready; recent months refreshed.");
        } catch (RuntimeException e) {
            log.error("Could not refresh monthly summary at startup (missing INSERT/UPDATE grant "
                    + "on report_monthly_summary?). Reports may use a stale/empty summary.", e);
        }
    }

    /** Nightly refresh (default 01:30, override via {@code txreport.summary.cron}). */
    @Scheduled(cron = "${txreport.summary.cron:0 30 1 * * *}")
    public void nightly() {
        if (!available) {
            return;
        }
        try {
            refreshRecent();
            log.info("report_monthly_summary refreshed (nightly).");
        } catch (RuntimeException e) {
            log.error("Nightly monthly-summary refresh failed", e);
        }
    }

    /** Recompute the current month + the previous one (catches late-arriving/corrected data). */
    private void refreshRecent() {
        YearMonth current = YearMonth.now(zone);
        refresh(current);
        refresh(current.minusMonths(1));
    }

    private void refresh(YearMonth ym) {
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        summary.refreshMonth(ym.toString(), start, end);
    }
}
