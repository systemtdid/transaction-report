package com.tdid.txreport.billing;

import java.math.BigDecimal;

/**
 * One progressive pricing band. {@code maxCapacity} is the WIDTH of the band
 * (the number of transactions it can hold), not a cumulative ceiling. The final
 * tier is unbounded ({@code maxCapacity == null}). See ARCHITECTURE.md §8.5.
 */
public record FeeTier(int tierNumber, Long maxCapacity, BigDecimal rate) {

    public boolean isUnbounded() {
        return maxCapacity == null;
    }
}
