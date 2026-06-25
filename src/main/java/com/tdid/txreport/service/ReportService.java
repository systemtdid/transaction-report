package com.tdid.txreport.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tdid.txreport.billing.BillingConfigRepository;
import com.tdid.txreport.config.OrgRegistryProperties;
import com.tdid.txreport.domain.OrgProfile;
import com.tdid.txreport.domain.ReportType;
import com.tdid.txreport.exception.UnknownOrgException;
import com.tdid.txreport.pdf.HtmlReportRenderer;
import com.tdid.txreport.pdf.PdfRenderService;
import com.tdid.txreport.repository.TransactionRepository;

@Service
public class ReportService {

    private final Map<String, OrgProfile> registry;
    private final DailyReportService dailyService;
    private final MonthlyReportService monthlyService;
    private final HtmlReportRenderer htmlRenderer;
    private final PdfRenderService pdfRenderer;
    private final TransactionRepository transactionRepo;
    private final BillingConfigRepository billingConfigRepo;
    private final ZoneId zone;

    // Org list changes rarely; cache it briefly so the index page doesn't re-scan per hit.
    private static final long ORG_CACHE_TTL_MS = 300_000L;
    private volatile List<OrgProfile> cachedOrgs;
    private volatile long orgsCacheExpiryMs;

    public ReportService(OrgRegistryProperties props,
                         DailyReportService dailyService,
                         MonthlyReportService monthlyService,
                         HtmlReportRenderer htmlRenderer,
                         PdfRenderService pdfRenderer,
                         TransactionRepository transactionRepo,
                         BillingConfigRepository billingConfigRepo,
                         @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.registry = props.buildRegistry();
        this.dailyService = dailyService;
        this.monthlyService = monthlyService;
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
        this.transactionRepo = transactionRepo;
        this.billingConfigRepo = billingConfigRepo;
        this.zone = ZoneId.of(zoneName);
    }

    public byte[] generate(String orgId, ReportType type, YearMonth period) {
        if (!registry.containsKey(orgId) && !transactionRepo.existsByOrgId(orgId)) {
            throw new UnknownOrgException(orgId);
        }
        OrgProfile org = resolveProfile(orgId);

        YearMonth effectivePeriod = resolveDefaultPeriod(type, period);
        String html = buildHtml(org, type, effectivePeriod);
        return pdfRenderer.htmlToPdf(html);
    }

    /**
     * Org dropdown — derived from the orgIDs that actually have transactions in the DB
     * (busiest first). Names come from the configured registry first, then the billing
     * config, then fall back to the orgID itself.
     */
    public List<OrgProfile> listOrgs() {
        long now = System.currentTimeMillis();
        List<OrgProfile> cached = cachedOrgs;
        if (cached != null && now < orgsCacheExpiryMs) {
            return cached;
        }
        List<OrgProfile> fresh = transactionRepo.findOrgIdsWithData().stream()
                .map(this::resolveProfile)
                .toList();
        cachedOrgs = fresh;
        orgsCacheExpiryMs = now + ORG_CACHE_TTL_MS;
        return fresh;
    }

    /**
     * Resolve an {@link OrgProfile} for any orgID. Configured orgs keep their gateway
     * filter and fee schedule; orgs only present in the data are synthesised with a
     * display name (billing config → else the orgID) and no extra filters.
     */
    private OrgProfile resolveProfile(String orgId) {
        OrgProfile configured = registry.get(orgId);
        if (configured != null) {
            return configured;
        }
        String name = billingConfigRepo.findByOrgId(orgId)
                .map(c -> c.orgName())
                .orElse(orgId);
        return new OrgProfile(orgId, name, null, null);
    }

    private YearMonth resolveDefaultPeriod(ReportType type, YearMonth period) {
        if (period != null) return period;
        YearMonth now = YearMonth.now(zone);
        return type == ReportType.MONTHLY ? now.minusMonths(1) : now;
    }

    private String buildHtml(OrgProfile org, ReportType type, YearMonth period) {
        if (type == ReportType.DAILY) {
            return htmlRenderer.renderDaily(dailyService.build(org, period));
        } else {
            return htmlRenderer.renderMonthly(monthlyService.build(org, period));
        }
    }
}
