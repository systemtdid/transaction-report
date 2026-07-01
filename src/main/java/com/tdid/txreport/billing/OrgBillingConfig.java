package com.tdid.txreport.billing;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declarative per-organisation pricing. {@code null} optional fields switch that
 * logic off. See ARCHITECTURE.md §8.5.
 *
 * <ul>
 *   <li>{@code billingCycle}  — MONTHLY bills the month; YEARLY bills the year-to-date.</li>
 *   <li>{@code baseFee}       — flat fee added to the tier fee (any cycle).</li>
 *   <li>{@code minimumFee}    — floor: invoice never drops below it.</li>
 *   <li>{@code prepaidQuota}  — first N transactions of the year are free (not billed).</li>
 *   <li>{@code annualMaxCap}  — at most N billable transactions per year.</li>
 *   <li>{@code waiveIfBelowMinimum} — waive the invoice while YTD usage is below the minimum.</li>
 *   <li>{@code billingYearStartMonth} — month (1–12) the customer's billing year starts;
 *       drives the "accumulated" (YTD) window and the annual rules. Default 1 (January).</li>
 * </ul>
 */
public record OrgBillingConfig(
        String orgId,
        String orgName,
        BillingCycle billingCycle,
        BigDecimal baseFee,
        BigDecimal minimumFee,
        Long prepaidQuota,
        Long annualMaxCap,
        boolean waiveIfBelowMinimum,
        List<FeeTier> tiers,
        int billingYearStartMonth) {

    public boolean hasBaseFee()      { return baseFee != null; }
    public boolean hasMinimumFee()   { return minimumFee != null; }
    public boolean hasPrepaidQuota() { return prepaidQuota != null; }
    public boolean hasAnnualMaxCap() { return annualMaxCap != null; }

    public boolean isYearly() {
        return billingCycle == BillingCycle.YEARLY;
    }

    /**
     * Contractual minimum used by the waive rule — the first tier's capacity.
     * Returns 0 when there are no tiers or the first tier is unbounded.
     */
    public long minimumLimit() {
        if (tiers.isEmpty() || tiers.get(0).maxCapacity() == null) {
            return 0L;
        }
        return tiers.get(0).maxCapacity();
    }
}
