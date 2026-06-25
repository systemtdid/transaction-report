package com.tdid.txreport.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // Gateway list changes rarely; cache it briefly so the index page doesn't re-scan per hit.
    private static final long GATEWAY_CACHE_TTL_MS = 300_000L;
    private volatile List<OrgProfile> cachedGateways;
    private volatile long gatewaysCacheExpiryMs;

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

    public byte[] generate(String gatewayId, ReportType type, YearMonth period) {
        if (gatewayId == null || gatewayId.isBlank() || !transactionRepo.existsGateway(gatewayId)) {
            throw new UnknownOrgException(gatewayId);
        }
        OrgProfile subject = gatewaySubject(gatewayId);

        YearMonth effectivePeriod = resolveDefaultPeriod(type, period);
        String html = buildHtml(subject, type, effectivePeriod);
        return pdfRenderer.htmlToPdf(html);
    }

    /**
     * Gateway dropdown — gateway IDs that have billable transactions (busiest first), each
     * labelled with its representative organisation. The report is generated per gateway.
     */
    public List<OrgProfile> listGateways() {
        long now = System.currentTimeMillis();
        List<OrgProfile> cached = cachedGateways;
        if (cached != null && now < gatewaysCacheExpiryMs) {
            return cached;
        }
        List<OrgProfile> fresh = transactionRepo.findGatewayIdsWithData().stream()
                .map(this::gatewaySubject)
                .toList();
        cachedGateways = fresh;
        gatewaysCacheExpiryMs = now + GATEWAY_CACHE_TTL_MS;
        return fresh;
    }

    /**
     * The report subject for a gateway: the gateway itself (used for counting) plus the
     * representative org's id + name (used for the billing config lookup and the report header).
     */
    private OrgProfile gatewaySubject(String gatewayId) {
        List<String> orgIds = transactionRepo.findOrgIdsForGateway(gatewayId);
        String repOrgId = orgIds.isEmpty() ? null : orgIds.get(0);
        String name = repOrgId == null ? gatewayId : resolveName(repOrgId);
        // "Organization:" row — named orgs only ("Name - orgID"); orgs with no name are omitted.
        String organizations = orgIds.stream()
                .filter(id -> !resolveName(id).equals(id))
                .map(id -> resolveName(id) + " - " + id)
                .collect(Collectors.joining(", "));
        return new OrgProfile(repOrgId, name, gatewayId, organizations);
    }

    /** Display name for an orgID: registry override, else billing config, else the orgID. */
    private String resolveName(String orgId) {
        OrgProfile configured = registry.get(orgId);
        if (configured != null) {
            return configured.displayName();
        }
        return billingConfigRepo.findByOrgId(orgId)
                .map(c -> c.orgName())
                .orElse(orgId);
    }

    private YearMonth resolveDefaultPeriod(ReportType type, YearMonth period) {
        if (period != null) return period;
        YearMonth now = YearMonth.now(zone);
        return type == ReportType.MONTHLY ? now.minusMonths(1) : now;
    }

    private String buildHtml(OrgProfile subject, ReportType type, YearMonth period) {
        if (type == ReportType.DAILY) {
            return htmlRenderer.renderDaily(dailyService.build(subject, period));
        } else {
            return htmlRenderer.renderMonthly(monthlyService.build(subject, period));
        }
    }
}
