package com.tdid.txreport;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tdid.txreport.domain.FeeSchedule;
import com.tdid.txreport.domain.FeeTier;
import com.tdid.txreport.domain.MonthlyTierLine;
import com.tdid.txreport.service.FeeCalculator;

import static org.junit.jupiter.api.Assertions.*;

class FeeCalculatorTest {

    private final FeeCalculator calc = new FeeCalculator();

    private FeeSchedule defaultSchedule() {
        return new FeeSchedule(720_000, List.of(
                new FeeTier(1,       720_000L,   new BigDecimal("0.70")),
                new FeeTier(720_001, 1_440_000L, new BigDecimal("0.65")),
                new FeeTier(1_440_001, 2_160_000L, new BigDecimal("0.60")),
                new FeeTier(2_160_001, 2_880_000L, new BigDecimal("0.55")),
                new FeeTier(2_880_001, null,       new BigDecimal("0.50"))
        ));
    }

    @Test
    void verifiedSample_89741_allInTier1() {
        List<MonthlyTierLine> lines = calc.calculate(defaultSchedule(), 89_741);
        BigDecimal total = calc.totalFee(lines);
        // Verified from the real template: 89,741 × 0.70 = 62,818.70
        assertEquals(new BigDecimal("62818.70"), total);
        assertEquals(89_741L, lines.get(0).usageCount());
        // All other tiers should have zero usage
        for (int i = 1; i < lines.size(); i++) {
            assertEquals(0L, lines.get(i).usageCount());
        }
    }

    @Test
    void zeroUsage_zeroFee() {
        List<MonthlyTierLine> lines = calc.calculate(defaultSchedule(), 0);
        assertEquals(new BigDecimal("0.00"), calc.totalFee(lines));
    }

    @Test
    void spansMultipleTiers() {
        // 720,000 in tier1 + 100,000 in tier2
        long usage = 820_000;
        List<MonthlyTierLine> lines = calc.calculate(defaultSchedule(), usage);
        // tier1: 720,000 × 0.70 = 504,000.00
        assertEquals(new BigDecimal("504000.00"), lines.get(0).fee());
        // tier2: 100,000 × 0.65 = 65,000.00
        assertEquals(new BigDecimal("65000.00"), lines.get(1).fee());
        assertEquals(new BigDecimal("569000.00"), calc.totalFee(lines));
    }
}
