package com.tdid.txreport.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the billing rules (ARCHITECTURE.md §8.5). The engine is pure, so it is
 * exercised directly with hand-built configs, a period usage and a YTD accumulated usage.
 */
class BillingEngineServiceTest {

    private final BillingEngineService engine = new BillingEngineService();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // ---- Base fee (additive) -----------------------------------------------
    @Test
    void baseFee_addedToTieredFee() {
        // KTB: 50k @ 0.4280 = 21,400 ; + base 30,000 = 51,400
        BillingResult r = engine.calculate(ktb(), 50_000, 50_000);
        assertThat(r.billableUsage()).isEqualTo(50_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("21400.00"));
        assertThat(r.baseFee()).isEqualByComparingTo(bd("30000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("51400.00"));
    }

    // ---- Free tier + base, not waived (above the free threshold) ------------
    @Test
    void freeTier_thenBase_whenAboveThreshold() {
        // Tokio: 50k free + 10k @ 0.50 = 5,000 ; + base 35,500 = 40,500
        BillingResult r = engine.calculate(tokio(), 60_000, 60_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("5000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("40500.00"));
        assertThat(r.waived()).isFalse();
    }

    // ---- Waive while below the minimum (free-tier threshold) ----------------
    @Test
    void waive_belowMinimum_zeroesEverything() {
        // Tokio 30k accumulated < 50k threshold => waived (base fee not charged either)
        BillingResult r = engine.calculate(tokio(), 30_000, 30_000);
        assertThat(r.waived()).isTrue();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("0.00"));
    }

    // ---- Minimum-fee guarantee ---------------------------------------------
    @Test
    void minimumFee_raisesToFloor() {
        // MTI 50k @ 0.70 = 35,000 < 42,000 floor => billed 42,000
        BillingResult r = engine.calculate(mti(), 50_000, 50_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("35000.00"));
        assertThat(r.minimumFeeApplied()).isTrue();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("42000.00"));
    }

    // ---- Prepaid quota: only usage above the annual quota is billed ---------
    @Test
    void prepaidQuota_billsOnlyExcessThisMonth() {
        // Prudential prepaid 514,285. Month adds 100k, taking YTD 700k -> 800k.
        // billable YTD: 800k-514285=285,715 ; before: 700k-514285=185,715 ; this month = 100,000
        BillingResult r = engine.calculate(prudential(), 100_000, 800_000);
        assertThat(r.billableUsage()).isEqualTo(100_000);
        assertThat(r.billedFee()).isEqualByComparingTo(bd("70000.00")); // 100k @ 0.70
        assertThat(r.waived()).isFalse();
    }

    @Test
    void prepaidQuota_stillWaivedWhileYtdBelowMinimum() {
        // YTD 600k < first-tier minimum 720k => waived even though some is above prepaid
        BillingResult r = engine.calculate(prudential(), 100_000, 600_000);
        assertThat(r.billableUsage()).isEqualTo(85_715); // 600k-514285
        assertThat(r.waived()).isTrue();
        assertThat(r.billedFee()).isEqualByComparingTo(bd("0.00"));
    }

    // ---- Annual max cap: billable transactions per year are capped ----------
    @Test
    void annualMaxCap_limitsBillableTransactions() {
        // GSB cap 226,000. Month adds 100k, YTD 200k -> 300k.
        // billable YTD: min(300k,226k)=226k ; before: min(200k,226k)=200k ; this month = 26,000
        BillingResult r = engine.calculate(gsb(), 100_000, 300_000);
        assertThat(r.billableUsage()).isEqualTo(26_000);
        assertThat(r.billedFee()).isEqualByComparingTo(bd("45500.00")); // 26k @ 1.75
    }

    @Test
    void annualMaxCap_nothingBillableOnceReached() {
        // YTD already past the cap => no billable this month
        BillingResult r = engine.calculate(gsb(), 50_000, 300_000);
        assertThat(r.billableUsage()).isEqualTo(0);
        assertThat(r.billedFee()).isEqualByComparingTo(bd("0.00"));
    }

    // ---- Yearly cycle bills the full year-to-date ---------------------------
    @Test
    void yearlyCycle_billsAccumulatedUsage() {
        // Dhipaya YEARLY: YTD 300k -> 240k free + 60k @ 1.50 = 90,000
        BillingResult r = engine.calculate(dhipaya(), 50_000, 300_000);
        assertThat(r.billableUsage()).isEqualTo(300_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("90000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("90000.00"));
    }

    @Test
    void yearlyCycle_baseFeeAdded() {
        // BCI YEARLY: 30k free + 20k @ 3.00 = 60,000 ; + base 100,000 = 160,000
        BillingResult r = engine.calculate(bci(), 50_000, 50_000);
        assertThat(r.tieredFee()).isEqualByComparingTo(bd("60000.00"));
        assertThat(r.baseFee()).isEqualByComparingTo(bd("100000.00"));
        assertThat(r.billedFee()).isEqualByComparingTo(bd("160000.00"));
    }

    // ---- fixtures ----------------------------------------------------------
    private static FeeTier tier(int n, Long cap, String rate) {
        return new FeeTier(n, cap, bd(rate));
    }

    private static OrgBillingConfig ktb() {
        return new OrgBillingConfig("0107537000882", "KTB", BillingCycle.MONTHLY,
                bd("30000.00"), null, null, null, false, List.of(
                tier(1, 100_000L, "0.4280"), tier(2, 400_000L, "0.2500"),
                tier(3, 500_000L, "0.1950"), tier(4, 2_000_000L, "0.1870"),
                tier(5, null, "0.1840")));
    }

    private static OrgBillingConfig tokio() {
        return new OrgBillingConfig("0107540000103", "Tokio", BillingCycle.MONTHLY,
                bd("35500.00"), null, null, null, true, List.of(
                tier(1, 50_000L, "0.00"), tier(2, null, "0.50")));
    }

    private static OrgBillingConfig mti() {
        return new OrgBillingConfig("0107551000151", "MTI", BillingCycle.MONTHLY,
                null, bd("42000.00"), null, null, false, List.of(
                tier(1, 60_000L, "0.70"), tier(2, 60_000L, "0.65"),
                tier(3, 60_000L, "0.60"), tier(4, 60_000L, "0.55"),
                tier(5, null, "0.50")));
    }

    private static OrgBillingConfig prudential() {
        return new OrgBillingConfig("0107537001897", "Prudential", BillingCycle.MONTHLY,
                null, null, 514_285L, null, true, List.of(
                tier(1, 720_000L, "0.70"), tier(2, 720_000L, "0.65"),
                tier(3, 720_000L, "0.60"), tier(4, 720_000L, "0.55"),
                tier(5, null, "0.50")));
    }

    private static OrgBillingConfig gsb() {
        return new OrgBillingConfig("0994000164891", "GSB", BillingCycle.MONTHLY,
                null, bd("0.00"), null, 226_000L, false, List.of(
                tier(1, 240_000L, "1.75"), tier(2, 240_000L, "1.50"),
                tier(3, 240_000L, "1.25"), tier(4, 240_000L, "1.00"),
                tier(5, 240_000L, "0.75"), tier(6, null, "0.50")));
    }

    private static OrgBillingConfig dhipaya() {
        return new OrgBillingConfig("0107556000051", "Dhipaya", BillingCycle.YEARLY,
                null, null, null, null, true, List.of(
                tier(1, 240_000L, "0.00"), tier(2, null, "1.50")));
    }

    private static OrgBillingConfig bci() {
        return new OrgBillingConfig("0125562016973", "BCI", BillingCycle.YEARLY,
                bd("100000.00"), null, null, null, true, List.of(
                tier(1, 30_000L, "0.00"), tier(2, null, "3.00")));
    }
}
