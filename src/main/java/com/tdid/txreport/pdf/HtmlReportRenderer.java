package com.tdid.txreport.pdf;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.tdid.txreport.domain.DailyReportData;
import com.tdid.txreport.domain.MonthlyReportData;

@Component
public class HtmlReportRenderer {

    private final TemplateEngine templateEngine;

    public HtmlReportRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderDaily(DailyReportData data) {
        Context ctx = new Context();
        ctx.setVariable("data", data);
        return templateEngine.process("report/daily_report", ctx);
    }

    public String renderMonthly(MonthlyReportData data) {
        Context ctx = new Context();
        ctx.setVariable("data", data);
        return templateEngine.process("report/monthly_report", ctx);
    }
}
