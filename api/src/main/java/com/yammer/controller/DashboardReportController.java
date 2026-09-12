package com.yammer.controller;

import com.yammer.dto.DashboardResponse;
import com.yammer.dto.DashboardResponse.FinalRow;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.security.AccessGuard;
import com.yammer.service.BridgeService;
import com.yammer.service.DashboardReportService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/reports/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER','WATCHER')") // WATCHER = read-only live view of the dashboard
public class DashboardReportController {

    private final DashboardReportService reportService;
    private final BridgeService bridgeService;
    private final IntegrationRepository integrationRepository;
    private final AccessGuard accessGuard;

    /** Everything on the dashboard for one location and an inclusive date range (server-local days). */
    @GetMapping
    public DashboardResponse dashboard(
            @RequestParam UUID locationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.dashboard(locationId, from, to);
    }

    public record PrintFinalRequest(@NotNull UUID locationId, @NotNull LocalDate from, @NotNull LocalDate to,
                                    @NotNull UUID printerId) {
    }

    /** Print the final report on a thermal printer — one slip per waiter, as the old app did. */
    @PostMapping("/final/print")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')") // a side effect: not for watchers
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void printFinal(@RequestBody PrintFinalRequest request) {
        LocationEntity location = accessGuard.requireAccessibleLocation(request.locationId());
        IntegrationEntity printer = integrationRepository.findById(request.printerId())
                .filter(i -> i.getLocationId().equals(request.locationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Printer not found"));
        List<FinalRow> rows = reportService.finalReport(request.locationId(), request.from(), request.to());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing to print for this period");
        }
        String title = location.getName() + " " + (request.from().equals(request.to())
                ? request.from().toString() : request.from() + " - " + request.to());
        bridgeService.sendWaiterReport(printer, title, rows);
    }
}
