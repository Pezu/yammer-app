package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** Option lists for the orders-report column filters. */
public record OrderFilterOptionsResponse(List<PointOption> orderPoints, List<WaiterOption> waiters) {

    public record PointOption(UUID id, String name) {
    }

    /** {@code username} is the filter value; {@code name} the display label. */
    public record WaiterOption(String username, String name) {
    }
}
