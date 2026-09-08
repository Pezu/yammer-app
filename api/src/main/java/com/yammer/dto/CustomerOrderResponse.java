package com.yammer.dto;

import java.util.UUID;

/**
 * Result of a customer-placed order (public QR page). Pay-later → {@code orderId} set.
 * ONLINE self-pay → {@code paymentUrl} to redirect the browser to, plus the
 * {@code reference} the return page polls for the outcome.
 */
public record CustomerOrderResponse(
        UUID orderId,
        String paymentUrl,
        UUID reference,
        /** True when the order is a DRAFT (table in CONFIRM mode) and the waiter must accept it first. */
        boolean pendingApproval) {

    public static CustomerOrderResponse placed(UUID orderId, boolean pendingApproval) {
        return new CustomerOrderResponse(orderId, null, null, pendingApproval);
    }

    public static CustomerOrderResponse redirect(String paymentUrl, UUID reference) {
        return new CustomerOrderResponse(null, paymentUrl, reference, false);
    }
}
