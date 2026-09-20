package com.yammer.dto;

import com.yammer.dto.DashboardResponse.FinalRow;
import java.util.List;

/**
 * The cash-up of a closed service day: one row per waiter (the same shape the thermal
 * slip prints) plus whether the slips actually reached a printer.
 */
public record ShiftCloseResponse(
        ShiftResponse shift,
        List<FinalRow> rows,
        /** false when no printer was picked, none is configured, or the bridge refused the job. */
        boolean printed,
        /** Why the print did not happen (null when it did, or when none was asked for). */
        String printError) {
}
