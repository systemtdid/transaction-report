package com.tdid.txreport.domain;

import java.math.BigDecimal;

public record MonthlyTierLine(FeeTier tier, long usageCount, BigDecimal fee) {
}
