package com.tdid.txreport.billing;

import java.math.BigDecimal;

/** Outcome for one tier; maps 1:1 to a monthly-report tier-table row. See ARCHITECTURE.md §8.5. */
public record TierCharge(
        int tierNumber,
        Long maxCapacity,
        BigDecimal rate,
        long unitsInTier,
        BigDecimal charge) {
}
