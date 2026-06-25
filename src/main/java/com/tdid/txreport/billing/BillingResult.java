package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.util.List;

/** Full billing outcome for one organisation/period. See ARCHITECTURE.md §8.5. */
public record BillingResult(
        String orgId,
        long usage,
        List<TierCharge> tierBreakdown,
        BigDecimal tieredFee,
        BigDecimal fixedMonthlyFee,
        BigDecimal computedFee,
        BigDecimal billedFee,
        boolean minimumFeeApplied,
        boolean waived,
        String remark) {
}
