package com.tdid.txreport.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the five billing logics (ARCHITECTURE.md §8.5). The engine is
 * pure, so it is exercised directly with hand-built configs and known usages.
 */
class BillingEngineServiceTest {

    private final BillingEngineService engine = new BillingEngineService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ---- 1. Progressive Tiered Pricing -------------------------------------
    @Test
    void progressiveTiered_spillsAcrossTiers() {
        // Muang Thai tiers: 60k@0.70, 60k@0.65, ... ; 100k usage = 60k@0.70 + 40k@0.65
        OrgBillingConfig muangThai = muangThai();
        BillingResult r = engine.calculate(muangThai, 100_000);
        // 60000*0.70 = 42000 ; 40000*0.65 = 26000 ; total 68000
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("68000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("68000.00"));
        assertThat(r.minimumFeeApplied()).isFalse();
        assertThat(r.tierBreakdown()).hasSize(5);
        assertThat(r.tierBreakdown().get(0).unitsInTier()).isEqualTo(60_000);
        assertThat(r.tierBreakdown().get(1).unitsInTier()).isEqualTo(40_000);
        assertThat(r.tierBreakdown().get(2).unitsInTier()).isZero();
    }

    // ---- 2. Free Tier (Zero Rating) ----------------------------------------
    @Test
    void freeTier_chargesNothingForLeadingUnits() {
        // Tokio Marine: 50k free, then 0.50. 60k usage (>= 50k so no waive)
        OrgBillingConfig tokio = tokioMarine();
        BillingResult r = engine.calculate(tokio, 60_000);
        // 50000*0 + 10000*0.50 = 5000
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("5000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("5000.00"));
        assertThat(r.tierBreakdown().get(0).charge()).isEqualByComparingTo(bd("0.00"));
        assertThat(r.waived()).isFalse();
    }

    // ---- 3. Minimum Fee Guarantee ------------------------------------------
    @Test
    void minimumFeeGuarantee_raisesToFloor() {
        // Muang Thai minimumFee 42000. 50k usage => tiered 35000 < 42000 => billed 42000
        BillingResult r = engine.calculate(muangThai(), 50_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("35000.00"));
        assertThat(r.minimumFeeApplied()).isTrue();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("42000.00"));
    }

    // ---- 4. Fixed Monthly Fee (additive) -----------------------------------
    @Test
    void fixedMonthlyFee_addedToTiered() {
        // Krungthai: tiers + fixed 30000. 50k usage => 50000*0.4280 = 21400 ; +30000 = 51400
        BillingResult r = engine.calculate(krungthai(), 50_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("21400.00"));
        assertThat(r.fixedMonthlyFee()).isEqualByComparingTo(bd("30000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("51400.00"));
    }

    // ---- 5. Minimum Limit Waive --------------------------------------------
    @Test
    void waive_whenBelowMinimum_zeroesInvoice() {
        // Prudential: minLimit = first tier capacity 720000. 500k usage => waived
        BillingResult r = engine.calculate(prudential(), 500_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("350000.00")); // 500000*0.70
        assertThat(r.waived()).isTrue();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("0.00"));
        assertThat(r.remark()).contains("minimum limit");
    }

    @Test
    void waive_doesNotApplyAtOrAboveMinimum() {
        // 800k usage >= 720000 => not waived ; 720000*0.70 + 80000*0.65 = 556000
        BillingResult r = engine.calculate(prudential(), 800_000);
        assertThat(r.waived()).isFalse();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("556000.00"));
    }

    // ---- fixtures ----------------------------------------------------------
    private static OrgBillingConfig prudential() {
        return new OrgBillingConfig("0107537001897", "Prudential", null, null, true, List.of(
                new FeeTier(1, 720_000L, bd("0.70")),
                new FeeTier(2, 720_000L, bd("0.65")),
                new FeeTier(3, 720_000L, bd("0.60")),
                new FeeTier(4, 720_000L, bd("0.55")),
                new FeeTier(5, null, bd("0.50"))));
    }

    private static OrgBillingConfig tokioMarine() {
        return new OrgBillingConfig("0107540000103", "Tokio Marine", null, null, true, List.of(
                new FeeTier(1, 50_000L, bd("0.00")),
                new FeeTier(2, null, bd("0.50"))));
    }

    private static OrgBillingConfig muangThai() {
        return new OrgBillingConfig("0107551000151", "Muang Thai", bd("42000.00"), null, false, List.of(
                new FeeTier(1, 60_000L, bd("0.70")),
                new FeeTier(2, 60_000L, bd("0.65")),
                new FeeTier(3, 60_000L, bd("0.60")),
                new FeeTier(4, 60_000L, bd("0.55")),
                new FeeTier(5, null, bd("0.50"))));
    }

    private static OrgBillingConfig krungthai() {
        return new OrgBillingConfig("0107537000882", "Krungthai Bank", null, bd("30000.00"), false, List.of(
                new FeeTier(1, 100_000L, bd("0.4280")),
                new FeeTier(2, 400_000L, bd("0.2500")),
                new FeeTier(3, 500_000L, bd("0.1950")),
                new FeeTier(4, 2_000_000L, bd("0.1870")),
                new FeeTier(5, null, bd("0.1840"))));
    }
}
