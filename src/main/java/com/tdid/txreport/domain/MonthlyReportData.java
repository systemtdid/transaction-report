package com.tdid.txreport.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public record MonthlyReportData(
        OrgProfile org,
        YearMonth period,
        List<MonthlyTierLine> lines,
        long totalUsage,
        BigDecimal computedFee,
        BigDecimal fixedMonthlyFee,
        boolean minimumFeeApplied,
        BigDecimal billedFee,
        boolean belowMinimum,
        long accumulatedTransactions,
        String remark,
        LocalDateTime runStamp) {
}
