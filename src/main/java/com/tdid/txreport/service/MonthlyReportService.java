package com.tdid.txreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import com.tdid.txreport.repository.TransactionRepository;

@Service
public class MonthlyReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final TransactionRepository repo;
    private final BillingConfigRepository billingConfigRepo;
    private final BillingEngineService billingEngine;
    private final ZoneId zone;

    public MonthlyReportService(TransactionRepository repo,
                                BillingConfigRepository billingConfigRepo,
                                BillingEngineService billingEngine,
                                @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.repo = repo;
        this.billingConfigRepo = billingConfigRepo;
        this.billingEngine = billingEngine;
        this.zone = ZoneId.of(zoneName);
    }

    public MonthlyReportData build(OrgProfile org, YearMonth period) {
        LocalDate monthStart = period.atDay(1);
        LocalDate endExclusive = period.plusMonths(1).atDay(1);
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = endExclusive.atStartOfDay();

        long usage = repo.countSuccess(org.orgId(), org.gatewayId(), start, end);

        // Accumulated = year-to-date in the same calendar year (display only).
        LocalDateTime yearStart = LocalDate.of(period.getYear(), 1, 1).atStartOfDay();
        long accumulated = repo.countSuccess(org.orgId(), org.gatewayId(), yearStart, end);

        List<MonthlyTierLine> lines;
        BigDecimal tieredTotal;     // sum of the tier rows (the tier-table Total)
        BigDecimal fixedMonthlyFee; // additive fixed fee (0 if none)
        boolean minimumFeeApplied;  // min-fee floor raised the invoice
        BigDecimal billedFee;       // final invoice amount
        boolean belowMinimum;
        String remark;

        Optional<OrgBillingConfig> config = billingConfigRepo.findByOrgId(org.orgId());
        if (config.isPresent()) {
            BillingResult result = billingEngine.calculate(config.get(), usage);
            lines = toTierLines(result.tierBreakdown());
            tieredTotal = result.tieredFee();
            fixedMonthlyFee = result.fixedMonthlyFee();
            minimumFeeApplied = result.minimumFeeApplied();
            billedFee = result.billedFee();
            belowMinimum = result.waived();
            remark = result.remark();
        } else {
            // No billing config for this org — usage only, no invoice.
            lines = List.of();
            tieredTotal = ZERO;
            fixedMonthlyFee = ZERO;
            minimumFeeApplied = false;
            billedFee = ZERO;
            belowMinimum = false;
            remark = null;
        }

        LocalDateTime runStamp = LocalDateTime.now(zone);

        return new MonthlyReportData(
                org, period, lines, usage,
                tieredTotal, fixedMonthlyFee, minimumFeeApplied, billedFee, belowMinimum,
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
