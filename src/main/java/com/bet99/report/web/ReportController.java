package com.bet99.report.web;

import com.bet99.report.service.CsvExportService;
import com.bet99.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;

/**
 * The whole UI is one GET-driven page: the search form, the sort links and the
 * pagination links all submit the same criteria as query parameters. That keeps
 * every view of the report bookmarkable and shareable, and makes the back
 * button behave.
 */
@Controller
public class ReportController {

    private static final String VIEW = "report";

    private final ReportService reportService;
    private final CsvExportService csvExportService;

    public ReportController(ReportService reportService, CsvExportService csvExportService) {
        this.reportService = reportService;
        this.csvExportService = csvExportService;
    }

    /**
     * Supplies the form-backing object <em>before</em> request parameters are
     * bound, which is the only point at which a default can be set without
     * weakening validation.
     *
     * <p>On a first visit ({@code generate} absent) the date range is prefilled
     * from the table's own data window, so the page opens on real rows. Once the
     * form has been submitted the object starts empty, so a cleared date really
     * is missing and {@code @NotNull} reports it.
     *
     * <p>Setting the defaults after binding instead would not work: the fields
     * would already have failed validation, and Spring renders a rejected
     * field's original (empty) value rather than the bean's.
     */
    @ModelAttribute("criteria")
    public ReportCriteria criteria(@RequestParam(name = "generate", required = false) String generate) {
        ReportCriteria criteria = new ReportCriteria();
        if (generate == null) {
            LocalDateTime[] range = reportService.defaultRange();
            criteria.setStartDateTime(range[0]);
            criteria.setEndDateTime(range[1]);
        }
        return criteria;
    }

    @GetMapping({ "/", "/report" })
    public String report(@Valid @ModelAttribute("criteria") ReportCriteria criteria,
                         BindingResult binding,
                         @RequestParam(name = "generate", required = false) String generate,
                         Model model) {

        boolean submitted = generate != null;
        reportService.normalize(criteria);

        model.addAttribute("submitted", submitted);
        model.addAttribute("tranTypes", reportService.tranTypes());
        model.addAttribute("pageSizes", reportService.pageSizes());
        model.addAttribute("amountColumns", reportService.amountColumns());
        model.addAttribute("balanceColumns", reportService.balanceColumns());
        model.addAttribute("filterQuery", criteria.toFilterQueryString());

        if (!submitted || binding.hasErrors()) {
            return VIEW;
        }

        model.addAttribute("reportPage", reportService.generate(criteria));
        model.addAttribute("filterQuery", criteria.toFilterQueryString());
        return VIEW;
    }

    @GetMapping(value = "/report.csv", produces = "text/csv")
    public void exportCsv(@Valid @ModelAttribute("criteria") ReportCriteria criteria,
                          BindingResult binding,
                          HttpServletResponse response) throws IOException {

        if (binding.hasErrors()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid report criteria: a valid start/end date range is required");
            return;
        }

        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/csv");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + csvExportService.fileName(criteria) + "\"");

        try (Writer writer = response.getWriter()) {
            csvExportService.write(criteria, writer);
        }
    }
}
