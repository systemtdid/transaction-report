package com.tdid.txreport.domain;

import java.time.YearMonth;
import java.util.List;

public record DailyReportData(
        OrgProfile org,
        YearMonth period,
        List<DailyCount> rows,
        long total) {
}
