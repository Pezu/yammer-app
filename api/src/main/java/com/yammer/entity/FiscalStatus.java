package com.yammer.entity;

/** Fiscal receipt state of a payment — see V31 and {@code BridgeService}. */
public enum FiscalStatus {
    /** Nothing to fiscalize (PROTOCOL / PO, or a card payment not yet confirmed). */
    NONE,
    PENDING,
    /** Terminal: a printed fiscal receipt cannot un-print. */
    SUCCESS,
    FAILED,
    /**
     * A print attempt may or may not have produced a fiscal document (e.g. the bridge died
     * between closing the receipt and journaling it). Deliberately NOT retryable from the
     * UI: an operator verifies on the register and resolves it to SUCCESS or FAILED.
     */
    UNKNOWN
}
