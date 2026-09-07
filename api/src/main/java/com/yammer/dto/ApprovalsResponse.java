package com.yammer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The waiter's Approvals view: customer devices asking to join one of my tables,
 * and customer orders awaiting confirmation (CONFIRM self-order mode).
 */
public record ApprovalsResponse(List<CustomerApprovalRow> customers, List<OrderReportRow> orders) {

    public record CustomerApprovalRow(
            UUID id, UUID orderPointId, String orderPointName, Instant requestedAt) {
    }
}
