package com.tdid.txreport.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.tdid.txreport.domain.ReportType;
import com.tdid.txreport.service.ReportService;

@Controller
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("orgs", reportService.listOrgs());
        return "index";
    }

    @GetMapping("/report")
    public ResponseEntity<byte[]> download(
            @RequestParam String orgId,
            @RequestParam String type,
            @RequestParam(required = false) String period) {

        ReportType reportType = parseType(type);
        YearMonth yearMonth = parsePeriod(period);

        byte[] pdf = reportService.generate(orgId, reportType, yearMonth);

        String periodStr = yearMonth != null ? yearMonth.toString()
                : YearMonth.now().toString();
        String filename = type.toLowerCase() + "_" + orgId + "_" + periodStr + ".pdf";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    private ReportType parseType(String type) {
        try {
            return ReportType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid report type: " + type + ". Must be DAILY or MONTHLY.");
        }
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) return null;
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid period: " + period + ". Expected format: yyyy-MM");
        }
    }
}
