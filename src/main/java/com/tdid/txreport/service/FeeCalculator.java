package com.tdid.txreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tdid.txreport.domain.FeeSchedule;
import com.tdid.txreport.domain.FeeTier;
import com.tdid.txreport.domain.MonthlyTierLine;

@Component
public class FeeCalculator {

    public List<MonthlyTierLine> calculate(FeeSchedule schedule, long totalUsage) {
        List<MonthlyTierLine> lines = new ArrayList<>();
        long remaining = totalUsage;

        for (FeeTier tier : schedule.tiers()) {
            long tierCapacity = tier.to() == null
                    ? Long.MAX_VALUE
                    : tier.to() - tier.from() + 1;

            long usedInTier = Math.min(remaining, tierCapacity);
            if (usedInTier < 0) usedInTier = 0;

            BigDecimal fee = BigDecimal.valueOf(usedInTier)
                    .multiply(tier.rate())
                    .setScale(2, RoundingMode.HALF_UP);

            lines.add(new MonthlyTierLine(tier, usedInTier, fee));
            remaining -= usedInTier;
            if (remaining <= 0) break;
        }

        // Pad remaining tiers with zero lines
        int filled = lines.size();
        for (int i = filled; i < schedule.tiers().size(); i++) {
            lines.add(new MonthlyTierLine(schedule.tiers().get(i), 0, BigDecimal.ZERO.setScale(2)));
        }

        return lines;
    }

    public BigDecimal totalFee(List<MonthlyTierLine> lines) {
        return lines.stream()
                .map(MonthlyTierLine::fee)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
