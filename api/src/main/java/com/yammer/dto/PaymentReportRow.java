package com.yammer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row of the payments report: who took what at which table, and how. */
public record PaymentReportRow(
        UUID id,
        String orderPointName,
        String waiter,
        BigDecimal amount,
        BigDecimal tip,
        BigDecimal total,
        String paymentType,
        Instant createdAt,
        /** NONE / PENDING / SUCCESS / FAILED / UNKNOWN — see FiscalStatus. */
        String fiscalStatus,
        String receiptNumber) {
}
