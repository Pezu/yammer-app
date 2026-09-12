package com.yammer.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the backoffice dashboard shows for one location and date range. Money is
 * {@code sum(price × quantity)} of order lines; a line is paid when it carries a payment.
 * Payments and tips are attributed by the payment's own time and creator.
 */
public record DashboardResponse(
        Summary summary,
        /* gapless timeline: ordered amount + order count by order time, paid amount by payment time */
        List<Bucket> series,
        int bucketMinutes,
        List<TableRow> tables,
        List<ProductRow> products,
        List<WaiterRow> waiters,
        List<PaymentTypeRow> paymentTypes,
        /* the "final report": per waiter, card/cash takings and tips (CASH/CARD payment types only) */
        List<FinalRow> finalReport) {

    public record Summary(
            BigDecimal ordered, BigDecimal paid, BigDecimal tips, BigDecimal remaining,
            long orders, long payments, BigDecimal averageOrder,
            /* ordered on protocol tables (points that accept the PROTOCOL payment type) */
            BigDecimal orderedProtocol,
            /* settled with the PROTOCOL payment type (comped, no money) */
            BigDecimal paidProtocol) {
    }

    public record Bucket(String at, BigDecimal ordered, BigDecimal paid, long orders) {
    }

    public record TableRow(String table, boolean protocol, BigDecimal ordered, BigDecimal paidCash,
                           BigDecimal paidCard, BigDecimal paidProtocol, BigDecimal paidOther, BigDecimal tips,
                           BigDecimal remaining) {
    }

    public record ProductRow(String product, long quantity, BigDecimal sales) {
    }

    public record WaiterRow(String waiter, long orders, BigDecimal sales, BigDecimal paidCash,
                            BigDecimal paidCard, BigDecimal paidProtocol, BigDecimal paidOther,
                            BigDecimal tipsCash, BigDecimal tipsCard, BigDecimal unsettled) {
    }

    public record PaymentTypeRow(String type, long count, BigDecimal amount, BigDecimal tips) {
    }

    public record FinalRow(String waiter, BigDecimal paidCard, BigDecimal paidCash, BigDecimal tipCard,
                           BigDecimal tipCash, BigDecimal total) {
    }
}
