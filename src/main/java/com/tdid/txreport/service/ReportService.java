package com.tdid.txreport.service;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tdid.txreport.config.OrgRegistryProperties;
import com.tdid.txreport.domain.DailyReportData;
import com.tdid.txreport.domain.MonthlyReportData;
import com.tdid.txreport.domain.OrgProfile;
import com.tdid.txreport.domain.ReportType;
import com.tdid.txreport.exception.UnknownOrgException;
import com.tdid.txreport.pdf.HtmlReportRenderer;
import com.tdid.txreport.pdf.PdfRenderService;

@Service
public class ReportService {

    private final Map<String, OrgProfile> registry;
    private final List<OrgProfile> orgList;
    private final DailyReportService dailyService;
    private final MonthlyReportService monthlyService;
    private final HtmlReportRenderer htmlRenderer;
    private final PdfRenderService pdfRenderer;
    private final ZoneId zone;

    public ReportService(OrgRegistryProperties props,
                         DailyReportService dailyService,
                         MonthlyReportService monthlyService,
                         HtmlReportRenderer htmlRenderer,
                         PdfRenderService pdfRenderer,
                         @Value("${txreport.default-zone:Asia/Bangkok}") String zoneName) {
        this.registry = props.buildRegistry();
        this.orgList = props.buildList();
        this.dailyService = dailyService;
        this.monthlyService = monthlyService;
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
        this.zone = ZoneId.of(zoneName);
    }

    public byte[] generate(String orgId, ReportType type, YearMonth period) {
        OrgProfile org = registry.get(orgId);
        if (org == null) throw new UnknownOrgException(orgId);

        YearMonth effectivePeriod = resolveDefaultPeriod(type, period);
        String html = buildHtml(org, type, effectivePeriod);
        return pdfRenderer.htmlToPdf(html);
    }

    private YearMonth resolveDefaultPeriod(ReportType type, YearMonth period) {
        if (period != null) return period;
        YearMonth now = YearMonth.now(zone);
        return type == ReportType.MONTHLY ? now.minusMonths(1) : now;
    }

    private String buildHtml(OrgProfile org, ReportType type, YearMonth period) {
        if (type == ReportType.DAILY) {
            DailyReportData data = dailyService.build(org, period);
            return htmlRenderer.renderDaily(data);
        } else {
            MonthlyReportData data = monthlyService.build(org, period);
            return htmlRenderer.renderMonthly(data);
        }
    }

    public List<OrgProfile> listOrgs() {
        return orgList;
    }
}
