package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The customer-facing bill of the table's CURRENT session: the same aggregated lines the
 * waiter sees (unpaid + paid; {@code originalPrice} marks a split unit's halves), with the
 * totals — no payment types or modes (those are the waiter's business).
 */
public record CustomerBillResponse(
        List<OrderPointBillResponse.OrderPointBillLine> lines,
        BigDecimal total,
        BigDecimal unpaidTotal) {

    public static CustomerBillResponse from(OrderPointBillResponse bill) {
        return new CustomerBillResponse(bill.lines(), bill.total(), bill.unpaidTotal());
    }
}
