package com.yammer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One open table session: who opened it, when, and the outstanding (unpaid) amount. */
public record OpenTableReportRow(
        UUID sessionId,
        String orderPointName,
        String openedBy,
        Instant openedAt,
        BigDecimal amount) {
}
