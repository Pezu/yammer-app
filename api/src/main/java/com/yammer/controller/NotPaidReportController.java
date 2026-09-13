package com.yammer.controller;

import com.yammer.dto.NotPaidReportRow;
import com.yammer.service.NotPaidReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/not-paid")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // reporting is admin-only
public class NotPaidReportController {

    private final NotPaidReportService reportService;

    /** Consumption closed as PROTOCOL or PO in an inclusive date range, with product lines. */
    @GetMapping
    public List<NotPaidReportRow> report(
            @RequestParam UUID locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.report(locationId, from, to);
    }
}
