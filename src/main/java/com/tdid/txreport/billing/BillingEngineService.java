package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Stateless, pure billing engine. Given a config, the period usage and the
 * year-to-date accumulated usage, it computes one invoice applying, in order:
 *
 * <ol>
 *   <li><b>Prepaid quota</b> — the first {@code prepaidQuota} txns of the year are free.</li>
 *   <li><b>Annual max cap</b> — at most {@code annualMaxCap} billable txns per year.</li>
 *   <li><b>Billing cycle</b> — MONTHLY bills this period's new billable txns; YEARLY bills
 *       the whole year-to-date billable txns.</li>
 *   <li><b>Progressive tiered pricing</b> (a free tier is simply {@code rate == 0}).</li>
 *   <li><b>Base fee</b> — added to the tier fee.</li>
 *   <li><b>Minimum-fee guarantee</b> — floor.</li>
 *   <li><b>Minimum-limit waive</b> — final override to {@code 0.00} while YTD usage is below
 *       the minimum.</li>
 * </ol>
 * See ARCHITECTURE.md §8.5.2.
 */
@Service
public class BillingEngineService {

    static final String BELOW_MIN_REMARK =
            "Number of usage transaction has not exceeded the minimum limit";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public BillingResult calculate(OrgBillingConfig config, long periodUsage, long accumulatedUsage) {
        long period = Math.max(periodUsage, 0L);
        long accumulated = Math.max(accumulatedUsage, period); // accumulated must include this period
        long priorAccumulated = accumulated - period;

        // Rules 1 + 2 + 3: how many transactions are actually billable on this invoice.
        long billableThrough = billableOf(config, accumulated);       // billable YTD incl. this period
        long billableBefore = billableOf(config, priorAccumulated);   // billable YTD before this period
        long billableUsage = config.isYearly()
                ? billableThrough
                : Math.max(0L, billableThrough - billableBefore);     // MONTHLY: only this period's new billable

        // Rule 4: progressive tiered pricing (free tier = rate 0; every tier recorded).
        List<TierCharge> breakdown = new ArrayList<>(config.tiers().size());
        BigDecimal tieredFee = ZERO;
        long remaining = billableUsage;
        for (FeeTier tier : config.tiers()) {
            long units = tier.isUnbounded() ? remaining : Math.min(remaining, tier.maxCapacity());
            BigDecimal charge = tier.rate().multiply(BigDecimal.valueOf(units)).setScale(2, RoundingMode.HALF_UP);
            breakdown.add(new TierCharge(tier.tierNumber(), tier.maxCapacity(), tier.rate(), units, charge));
            tieredFee = tieredFee.add(charge);
            remaining -= units;
        }

        // Rule 5: base fee (additive).
        BigDecimal baseFee = config.hasBaseFee()
                ? config.baseFee().setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal computedFee = tieredFee.add(baseFee);

        // Rule 6: minimum-fee guarantee (floor).
        BigDecimal guaranteed = computedFee;
        boolean minimumFeeApplied = false;
        if (config.hasMinimumFee()) {
            BigDecimal floor = config.minimumFee().setScale(2, RoundingMode.HALF_UP);
            if (computedFee.compareTo(floor) < 0) {
                guaranteed = floor;
                minimumFeeApplied = true;
            }
        }

        // Minimum-limit waive (override → 0): YTD accumulated usage below the first-tier minimum.
        BigDecimal billedFee;
        boolean waived;
        String remark;
        if (config.waiveIfBelowMinimum() && accumulated < config.minimumLimit()) {
            billedFee = ZERO;
            waived = true;
            remark = BELOW_MIN_REMARK;
        } else {
            billedFee = guaranteed;
            waived = false;
            remark = null;
        }

        return new BillingResult(
                config.orgId(), config.billingCycle(), period, accumulated, billableUsage,
                List.copyOf(breakdown), tieredFee, baseFee, computedFee, billedFee,
                minimumFeeApplied, waived, remark);
    }

    /** Billable transaction count out of an accumulated total: prepaid is free, capped at the annual max. */
    private static long billableOf(OrgBillingConfig config, long accumulated) {
        long b = Math.max(0L, accumulated);
        if (config.hasPrepaidQuota()) {
            b = Math.max(0L, b - config.prepaidQuota());
        }
        if (config.hasAnnualMaxCap()) {
            b = Math.min(b, config.annualMaxCap());
        }
        return b;
    }
}
