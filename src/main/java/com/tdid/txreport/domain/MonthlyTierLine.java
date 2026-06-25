package com.tdid.txreport.domain;

import java.math.BigDecimal;

/** One display row of the monthly tier table (built from the Billing Engine's output). */
public record MonthlyTierLine(String rangeLabel, BigDecimal rate, long usageCount, BigDecimal fee) {
}
