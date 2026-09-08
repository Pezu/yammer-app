package com.yammer.event;

import java.util.UUID;

/**
 * Published (inside the payment transaction) once a payment must be fiscalized — CASH at
 * creation, CARD / ONLINE once confirmed, or a manual re-issue. Consumed AFTER_COMMIT by
 * {@code BridgeService}, which pushes the fiscal RECEIPT to the on-prem bridge.
 */
public record PaymentCommittedEvent(UUID paymentId) {
}
