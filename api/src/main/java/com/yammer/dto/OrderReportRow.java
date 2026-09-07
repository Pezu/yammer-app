package com.yammer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One row of the backoffice orders report. {@code paid} summarizes the order's lines:
 * NOT (nothing paid), PAR (partially), PAID (everything). {@code createdBy} is the
 * waiter's display name (or "Customer" for QR self-orders).
 */
public record OrderReportRow(
        UUID id,
        long orderNo,
        UUID orderPointId,
        String orderPointName,
        String createdBy,
        Instant createdAt,
        String status,
        List<Item> items,
        BigDecimal total,
        String paid) {

    public record Item(
            UUID id, UUID menuItemId, String name, BigDecimal price, long quantity, boolean paid) {
    }
}
