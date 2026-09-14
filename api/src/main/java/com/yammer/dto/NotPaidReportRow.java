package com.yammer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One settlement that brought no money — closed with the PROTOCOL or PO payment type — with
 * the product lines it covered.
 */
public record NotPaidReportRow(
        UUID paymentId,
        String orderPointName,
        /** the table's nickname from the backoffice, null when none */
        String nickname,
        String waiter,
        Instant at,
        String paymentType,
        BigDecimal amount,
        List<Line> items) {

    public record Line(String name, int quantity, BigDecimal price, BigDecimal total) {
    }
}
