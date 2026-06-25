package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declarative per-organisation pricing. A {@code null} {@code minimumFee} or
 * {@code fixedMonthlyFee} means that logic is not applied. See ARCHITECTURE.md §8.5.
 */
public record OrgBillingConfig(
        String orgId,
        String orgName,
        BigDecimal minimumFee,
        BigDecimal fixedMonthlyFee,
        boolean waiveIfBelowMinimum,
        List<FeeTier> tiers) {

    public boolean hasMinimumFee() {
        return minimumFee != null;
    }

    public boolean hasFixedMonthlyFee() {
        return fixedMonthlyFee != null;
    }

    /**
     * Contractual minimum used by the waive rule. ASSUMPTION (ARCHITECTURE.md
     * §8.5.5 #2): equal to the first tier's capacity. Returns 0 when there are no
     * tiers or the first tier is unbounded (so the waive never triggers).
     */
    public long minimumLimit() {
        if (tiers.isEmpty() || tiers.get(0).maxCapacity() == null) {
            return 0L;
        }
        return tiers.get(0).maxCapacity();
    }
}
