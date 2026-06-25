package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Stateless, pure billing engine. Applies the five pricing logics in a fixed,
 * deterministic order:
 * <ol>
 *   <li>Progressive tiered pricing (a free tier is simply {@code rate == 0})</li>
 *   <li>Fixed monthly fee (additive)</li>
 *   <li>Minimum-fee guarantee (floor)</li>
 *   <li>Minimum-limit waive (final override to {@code 0.00})</li>
 * </ol>
 * See ARCHITECTURE.md §8.5.2.
 */
@Service
public class BillingEngineService {

    static final String BELOW_MIN_REMARK =
            "Number of usage transaction has not exceeded the minimum limit";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public BillingResult calculate(OrgBillingConfig config, long usage) {
        long normalisedUsage = Math.max(usage, 0L);

        // 1 + 2. Progressive tiered pricing (free tier = rate 0; every tier recorded).
        List<TierCharge> breakdown = new ArrayList<>(config.tiers().size());
        BigDecimal tieredFee = ZERO;
        long remaining = normalisedUsage;
        for (FeeTier tier : config.tiers()) {
            long units = tier.isUnbounded()
                    ? remaining
                    : Math.min(remaining, tier.maxCapacity());
            BigDecimal charge = tier.rate()
                    .multiply(BigDecimal.valueOf(units))
                    .setScale(2, RoundingMode.HALF_UP);
            breakdown.add(new TierCharge(tier.tierNumber(), tier.maxCapacity(),
                    tier.rate(), units, charge));
            tieredFee = tieredFee.add(charge);
            remaining -= units;
        }

        // 3. Fixed monthly fee (additive).
        BigDecimal fixedFee = config.hasFixedMonthlyFee()
                ? config.fixedMonthlyFee().setScale(2, RoundingMode.HALF_UP)
                : ZERO;
        BigDecimal computedFee = tieredFee.add(fixedFee);

        // 4. Minimum-fee guarantee (floor).
        BigDecimal guaranteed = computedFee;
        boolean minimumFeeApplied = false;
        if (config.hasMinimumFee()) {
            BigDecimal floor = config.minimumFee().setScale(2, RoundingMode.HALF_UP);
            if (computedFee.compareTo(floor) < 0) {
                guaranteed = floor;
                minimumFeeApplied = true;
            }
        }

        // 5. Minimum-limit waive (final override → 0.00; wins over the above).
        BigDecimal billedFee;
        boolean waived;
        String remark;
        if (config.waiveIfBelowMinimum() && normalisedUsage < config.minimumLimit()) {
            billedFee = ZERO;
            waived = true;
            remark = BELOW_MIN_REMARK;
        } else {
            billedFee = guaranteed;
            waived = false;
            remark = null;
        }

        return new BillingResult(
                config.orgId(), normalisedUsage, List.copyOf(breakdown),
                tieredFee, fixedFee, computedFee, billedFee,
                minimumFeeApplied, waived, remark);
    }
}
