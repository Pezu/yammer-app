package com.yammer.controller;

import com.yammer.dto.ShiftCloseResponse;
import com.yammer.dto.ShiftResponse;
import com.yammer.service.ShiftService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The location's service day, opened and closed by a manager. The waiter surface has no
 * endpoint here — it simply reports on whatever shift is open (see {@code ShiftService}).
 */
@RestController
@RequestMapping("/shifts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // opening/closing a day is a manager's job
public class ShiftController {

    private final ShiftService shiftService;

    /** The location's service days, newest first (the Shifts page, the dashboard's period combo). */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER','WATCHER')") // reading the days is fine for a wall display
    public List<ShiftResponse> list(@RequestParam UUID locationId) {
        return shiftService.list(locationId);
    }

    /** The open service day, or the last closed one, or null if this location never had one. */
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER','WATCHER')")
    public ShiftResponse current(@RequestParam UUID locationId) {
        return shiftService.current(locationId);
    }

    /** Table names that still owe something — while any exist, the day cannot close. */
    @GetMapping("/open-tables")
    public List<String> openTables(@RequestParam UUID locationId) {
        return shiftService.openTables(locationId);
    }

    /** Start a service day, optionally labelled. */
    @PostMapping("/open")
    public ShiftResponse open(@RequestBody OpenRequest body) {
        return shiftService.open(body.locationId(), body.nickname());
    }

    /** Rename a service day (the manager's own label). */
    @PutMapping("/{id}")
    public ShiftResponse rename(@PathVariable UUID id, @RequestBody RenameRequest body) {
        return shiftService.rename(id, body.nickname());
    }

    /** Close the day and cash up; with a printerId the slips also go to that thermal printer. */
    @PostMapping("/close")
    public ShiftCloseResponse close(@RequestBody CloseRequest body) {
        return shiftService.close(body.locationId(), body.printerId());
    }

    public record OpenRequest(@NotNull UUID locationId, String nickname) {
    }

    public record RenameRequest(String nickname) {
    }

    public record CloseRequest(@NotNull UUID locationId, UUID printerId) {
    }
}
