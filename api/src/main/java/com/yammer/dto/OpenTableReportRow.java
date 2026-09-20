package com.yammer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One table in service: who works it, since when, and the outstanding (unpaid) amount. */
public record OpenTableReportRow(
        /** The order point — the row's identity now that sittings have no id of their own. */
        UUID orderPointId,
        String orderPointName,
        String openedBy,
        Instant openedAt,
        BigDecimal amount) {
}
