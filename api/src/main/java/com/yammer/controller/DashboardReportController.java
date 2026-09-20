package com.yammer.controller;

import com.yammer.dto.DashboardResponse;
import com.yammer.dto.DashboardResponse.FinalRow;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.repository.IntegrationRepository;
import com.yammer.security.AccessGuard;
import com.yammer.service.BridgeService;
import com.yammer.dto.ShiftResponse;
import com.yammer.service.DashboardReportService;
import com.yammer.service.ShiftService;
import com.yammer.service.WaiterStatementPdfService;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final WaiterStatementPdfService statementPdfService;
    private final BridgeService bridgeService;
    private final IntegrationRepository integrationRepository;
    private final ShiftService shiftService;
    private final AccessGuard accessGuard;

    /**
     * Everything on the dashboard for one location, over one service day ({@code shiftId} —
     * the usual case, an open day running up to now) or an inclusive date range.
     */
    @GetMapping
    public DashboardResponse dashboard(
            @RequestParam UUID locationId,
            @RequestParam(required = false) UUID shiftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (shiftId != null) {
            ShiftService.Window w = shiftWindow(locationId, shiftId);
            return reportService.dashboardForShift(locationId, shiftId, w.from(), w.to());
        }
        requireRange(from, to);
        return reportService.dashboard(locationId, from, to);
    }

    /** The shift's window, once it is confirmed to belong to this location. */
    private ShiftService.Window shiftWindow(UUID locationId, UUID shiftId) {
        ShiftResponse shift = shiftService.get(shiftId);
        if (!shift.locationId().equals(locationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That service day belongs to another location");
        }
        return shiftService.windowOf(shiftId);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pass either shiftId or from+to");
        }
    }

    /**
     * One waiter's statement as a PDF: every payment they took in the period, grouped by table, with the
     * payment type and the products it covered.
     */
    @GetMapping(value = "/waiter/{username}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> waiterPdf(
            @PathVariable String username,
            @RequestParam UUID locationId,
            @RequestParam(required = false) UUID shiftId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String safe = username.replaceAll("[^A-Za-z0-9._-]", "_");
        byte[] pdf;
        String suffix;
        if (shiftId != null) {
            ShiftService.Window w = shiftWindow(locationId, shiftId);
            String period = ShiftService.label(shiftService.get(shiftId));
            pdf = statementPdfService.statementForShift(locationId, username, shiftId, period);
            suffix = w.from().toLocalDate().toString();
        } else {
            requireRange(from, to);
            pdf = statementPdfService.statement(locationId, username, from, to);
            suffix = from + "_" + to;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"waiter-" + safe + "-" + suffix + ".pdf\"")
                .body(pdf);
    }

    public record PrintFinalRequest(@NotNull UUID locationId, UUID shiftId, LocalDate from, LocalDate to,
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
        List<FinalRow> rows;
        String period;
        if (request.shiftId() != null) {
            ShiftService.Window w = shiftWindow(request.locationId(), request.shiftId());
            rows = reportService.finalReportForShift(request.locationId(), request.shiftId(), w.from(), w.to());
            period = ShiftService.label(shiftService.get(request.shiftId()));
        } else {
            requireRange(request.from(), request.to());
            rows = reportService.finalReport(request.locationId(), request.from(), request.to());
            period = request.from().equals(request.to())
                    ? request.from().toString() : request.from() + " - " + request.to();
        }
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing to print for this period");
        }
        String title = location.getName() + " " + period;
        bridgeService.sendWaiterReport(printer, title, rows);
    }
}
