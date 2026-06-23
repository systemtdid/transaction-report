package com.tdid.txreport.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tdid.txreport.domain.DailyCount;
import com.tdid.txreport.domain.DailyReportData;
import com.tdid.txreport.domain.OrgProfile;
import com.tdid.txreport.repository.TransactionRepository;

@Service
public class DailyReportService {

    private final TransactionRepository repo;

    public DailyReportService(TransactionRepository repo) {
        this.repo = repo;
    }

    public DailyReportData build(OrgProfile org, YearMonth period) {
        LocalDate monthStart = period.atDay(1);
        LocalDate endExclusive = period.plusMonths(1).atDay(1);

        List<DailyCount> dbRows = repo.countSuccessByDay(
                org.orgId(), org.gatewayId(), monthStart, endExclusive);

        // Index DB results by date for fast lookup
        Map<LocalDate, Long> countByDate = dbRows.stream()
                .collect(Collectors.toMap(DailyCount::date, DailyCount::count));

        // Zero-fill every calendar day of the month
        List<DailyCount> rows = new ArrayList<>(period.lengthOfMonth());
        long total = 0;
        for (int day = 1; day <= period.lengthOfMonth(); day++) {
            LocalDate date = period.atDay(day);
            long count = countByDate.getOrDefault(date, 0L);
            rows.add(new DailyCount(date, count));
            total += count;
        }

        return new DailyReportData(org, period, rows, total);
    }
}
