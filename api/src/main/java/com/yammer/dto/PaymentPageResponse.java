package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;

/** One page of a location's payments plus the totals over ALL of them (not just the page). */
public record PaymentPageResponse(
        List<PaymentReportRow> content, long total, int page, int size, Totals totals) {

    public record Totals(BigDecimal amount, BigDecimal tip, BigDecimal total) {
    }
}
