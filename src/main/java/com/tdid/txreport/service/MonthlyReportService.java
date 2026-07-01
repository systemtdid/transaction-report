package com.tdid.txreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tdid.txreport.billing.BillingConfigRepository;
import com.tdid.txreport.billing.BillingEngineService;
import com.tdid.txreport.billing.BillingResult;
import com.tdid.txreport.billing.OrgBillingConfig;
import com.tdid.txreport.billing.TierCharge;
import com.tdid.txreport.domain.MonthlyReportData;
import com.tdid.txreport.domain.MonthlyTierLine;
import com.tdid.txreport.domain.OrgProfile;
import com.tdid.txreport.repository.MonthlySummaryRepository;
import com.tdid.txreport.repository.TransactionRepository;

@Service
public class MonthlyReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TransactionRepository repo;
    private final MonthlySummaryRepository summaryRepo;
    private final SummaryMaintenanceService summaryMaintenance;
    private final BillingConfigRepository billingConfigRepo;
    private final BillingEngineService billingEngine;
    private final ZoneId zone;

    public MonthlyReportService(TransactionRepository repo,
                                MonthlySummaryRepository summaryRepo,
                                SummaryMaintenanceService summaryMaintenance,
                                BillingConfigRepository billingConfigRepo,
                                BillingEngineService billingEngine,
                                @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.repo = repo;
        this.summaryRepo = summaryRepo;
        this.summaryMaintenance = summaryMaintenance;
        this.billingConfigRepo = billingConfigRepo;
        this.billingEngine = billingEngine;
        this.zone = ZoneId.of(zoneName);
    }

    public MonthlyReportData build(OrgProfile org, YearMonth period) {
        Optional<OrgBillingConfig> config = billingConfigRepo.findByOrgId(org.orgId());

        // Billing year runs from billingYearStartMonth; the accumulated (YTD) window is the
        // range of months from the most recent cycle start up to the report month.
        int startMonth = config.map(OrgBillingConfig::billingYearStartMonth).orElse(1);
        YearMonth cycleStart = (period.getMonthValue() >= startMonth)
                ? YearMonth.of(period.getYear(), startMonth)
                : YearMonth.of(period.getYear() - 1, startMonth);

        long usage;
        long accumulated;
        if (summaryMaintenance.isAvailable()) {
            // Read pre-aggregated counts (fast at 4-10M rows/month).
            usage = summaryRepo.usage(org.gatewayId(), period.toString());
            accumulated = summaryRepo.accumulated(org.gatewayId(), cycleStart.toString(), period.toString());
        } else {
            // Fallback: scan transactions directly (gateway-scoped).
            LocalDateTime monthStart = period.atDay(1).atStartOfDay();
            LocalDateTime monthEnd = period.plusMonths(1).atDay(1).atStartOfDay();
            usage = repo.countSuccess(null, org.gatewayId(), monthStart, monthEnd);
            accumulated = repo.countSuccess(null, org.gatewayId(), cycleStart.atDay(1).atStartOfDay(), monthEnd);
        }

        List<MonthlyTierLine> lines;
        BigDecimal tieredTotal;     // sum of the tier rows (the tier-table Total)
        BigDecimal baseFee;         // additive base fee (0 if none)
        boolean minimumFeeApplied;  // min-fee floor raised the invoice
        BigDecimal billedFee;       // final invoice amount
        boolean belowMinimum;
        String remark;

        if (config.isPresent()) {
            BillingResult result = billingEngine.calculate(config.get(), usage, accumulated);
            lines = toTierLines(result.tierBreakdown());
            tieredTotal = result.tieredFee();
            baseFee = result.baseFee();
            minimumFeeApplied = result.minimumFeeApplied();
            billedFee = result.billedFee();
            belowMinimum = result.waived();
            remark = result.remark();
        } else {
            // No billing config for this org — usage only, no invoice.
            lines = List.of();
            tieredTotal = ZERO;
            baseFee = ZERO;
            minimumFeeApplied = false;
            billedFee = ZERO;
            belowMinimum = false;
            remark = null;
        }

        LocalDateTime runStamp = LocalDateTime.now(zone);

        return new MonthlyReportData(
                org, period, lines, usage,
                tieredTotal, baseFee, minimumFeeApplied, billedFee, belowMinimum,
                accumulated, remark, runStamp);
    }

    /**
     * Map the engine's per-tier charges to display rows, building cumulative
     * "from - to" range labels (the final unbounded tier renders as "from ++").
     */
    private List<MonthlyTierLine> toTierLines(List<TierCharge> charges) {
        List<MonthlyTierLine> lines = new ArrayList<>(charges.size());
        long cumulativeStart = 0;
        for (TierCharge tc : charges) {
            long from = cumulativeStart + 1;
            String label;
            if (tc.maxCapacity() == null) {
                label = String.format("%,d ++", from);
            } else {
                long to = cumulativeStart + tc.maxCapacity();
                label = String.format("%,d - %,d", from, to);
                cumulativeStart = to;
            }
            lines.add(new MonthlyTierLine(label, tc.rate(), tc.unitsInTier(), tc.charge()));
        }
        return lines;
    }
}
