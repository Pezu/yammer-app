package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The table's current round: its orders' lines aggregated per product, split
 * into unpaid and already-paid lines. {@code paymentTypeIds} = the payment types accepted
 * at this order point (the pay modal offers exactly these). {@code inService} tells the
 * UI whether a waiter works the table (Close table is offered while worked + fully paid).
 */
public record OrderPointBillResponse(
        String orderPointName,
        List<UUID> paymentTypeIds,
        boolean inService,
        String selfOrderMode,
        /* the point runs a tab; false = pay as you order (the waiter pays right after ordering) */
        boolean keepOpen,
        /* the table's discount, percent; null = none — the waiter's pay sheet shows Subtotal/Discount/Amount */
        BigDecimal discountPercent,
        List<OrderPointBillLine> lines,
        BigDecimal total,
        BigDecimal unpaidTotal) {

    /** {@code originalPrice} is set only on a partially-paid unit's remainder ("4.00 of 7.00"). */
    public record OrderPointBillLine(
            UUID menuItemId, String name, BigDecimal price, long quantity, boolean paid,
            BigDecimal originalPrice) {
    }
}
