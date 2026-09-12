package com.yammer.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One table on the waiter's Statistics page: what THIS waiter collected there (card/cash and
 * tips, by the payment's creator), what is still unpaid on its open session, and what was
 * comped with the PROTOCOL payment type.
 */
public record TableStatsResponse(
        UUID orderPointId,
        String name,
        BigDecimal paidCard,
        BigDecimal paidCash,
        BigDecimal tipCard,
        BigDecimal tipCash,
        BigDecimal unpaid,
        BigDecimal settled,
        boolean protocol) {
}
