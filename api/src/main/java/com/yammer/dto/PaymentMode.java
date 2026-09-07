package com.yammer.dto;

public enum PaymentMode {
    /** Settle every unpaid line of the table's bill. */
    FULL,
    /** Settle the requested product quantities, splitting lines as needed. */
    PARTIAL,
    /**
     * Settle a fixed sum, consuming products in the order they were ordered
     * (oldest order first); the last unit may end up partially paid.
     */
    AMOUNT
}
