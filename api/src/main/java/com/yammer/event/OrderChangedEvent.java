package com.yammer.event;

import com.yammer.entity.OrderEntity;

/**
 * Published (inside the order transaction) whenever an order enters or moves on the
 * service board: created (ORDERED), approved, READY / DELIVERED / CANCELED. Consumed
 * AFTER_COMMIT so screens are only told about changes that actually persisted, and the
 * WebSocket push runs off the request thread.
 *
 * @param order the affected order (detached by the time the listener runs)
 * @param type  the WebSocket message type, e.g. {@code ORDER_CREATED} / {@code ORDER_READY}
 */
public record OrderChangedEvent(OrderEntity order, String type) {
}
