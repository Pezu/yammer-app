package com.yammer.controller;

import com.yammer.dto.OpenTableReportRow;
import com.yammer.service.OpenTableReportService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports/open-tables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // reporting is admin-only
public class OpenTableReportController {

    private final OpenTableReportService reportService;

    /** One location's open table sessions with their outstanding amounts. */
    @GetMapping
    public List<OpenTableReportRow> report(@RequestParam UUID locationId) {
        return reportService.report(locationId);
    }
}
