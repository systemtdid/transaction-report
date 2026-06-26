package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.util.List;

/** Full billing outcome for one organisation invoice. See ARCHITECTURE.md §8.5. */
public record BillingResult(
        String orgId,
        BillingCycle billingCycle,
        long periodUsage,         // transactions in the billing period
        long accumulatedUsage,    // transactions year-to-date (incl. this period)
        long billableUsage,       // transactions actually tiered (after prepaid quota / annual cap)
        List<TierCharge> tierBreakdown,
        BigDecimal tieredFee,
        BigDecimal baseFee,
        BigDecimal computedFee,   // tieredFee + baseFee, before the floor/waive
        BigDecimal billedFee,     // final invoice amount
        boolean minimumFeeApplied,
        boolean waived,
        String remark) {
}
