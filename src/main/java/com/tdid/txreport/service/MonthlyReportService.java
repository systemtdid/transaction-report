package com.tdid.txreport.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tdid.txreport.domain.FeeSchedule;
import com.tdid.txreport.domain.MonthlyReportData;
import com.tdid.txreport.domain.MonthlyTierLine;
import com.tdid.txreport.domain.OrgProfile;
import com.tdid.txreport.repository.TransactionRepository;

@Service
public class MonthlyReportService {

    private static final String BELOW_MIN_REMARK =
            "Number of usage transaction has not exceeded the minimum limit";

    private final TransactionRepository repo;
    private final FeeCalculator feeCalculator;
    private final ZoneId zone;

    public MonthlyReportService(TransactionRepository repo,
                                FeeCalculator feeCalculator,
                                @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.repo = repo;
        this.feeCalculator = feeCalculator;
        this.zone = ZoneId.of(zoneName);
    }

    public MonthlyReportData build(OrgProfile org, YearMonth period) {
        // Report period: full selected month [start, endExclusive)
        LocalDate monthStart = period.atDay(1);
        LocalDate endExclusive = period.plusMonths(1).atDay(1);
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = endExclusive.atStartOfDay();

        long usage = repo.countSuccess(org.orgId(), org.gatewayId(), start, end);

        // Accumulated = year-to-date in the same calendar year
        LocalDateTime yearStart = LocalDate.of(period.getYear(), 1, 1).atStartOfDay();
        long accumulated = repo.countSuccess(org.orgId(), org.gatewayId(), yearStart, end);

        // Fee calculation
        FeeSchedule schedule = org.feeSchedule();
        List<MonthlyTierLine> lines;
        BigDecimal computedFee;
        BigDecimal billedFee;
        boolean belowMinimum;
        String remark;

        if (schedule != null) {
            lines = feeCalculator.calculate(schedule, usage);
            computedFee = feeCalculator.totalFee(lines);

            if (accumulated < schedule.minimumTransactions()) {
                billedFee = BigDecimal.ZERO.setScale(2);
                belowMinimum = true;
                remark = BELOW_MIN_REMARK;
            } else {
                billedFee = computedFee;
                belowMinimum = false;
                remark = null;
            }
        } else {
            // No fee schedule — just usage count, no billing
            lines = List.of();
            computedFee = BigDecimal.ZERO.setScale(2);
            billedFee = BigDecimal.ZERO.setScale(2);
            belowMinimum = false;
            remark = null;
        }

        LocalDateTime runStamp = LocalDateTime.now(zone);

        return new MonthlyReportData(
                org, period, lines, usage,
                computedFee, billedFee, belowMinimum,
                accumulated, remark, runStamp);
    }
}
