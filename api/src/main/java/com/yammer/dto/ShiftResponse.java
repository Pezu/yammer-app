package com.yammer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The location's service day. {@code open} false with an {@code openedAt} means the last
 * shift is closed; a null shift altogether means the location has never opened one, and
 * the waiter screens fall back to the current business day.
 */
public record ShiftResponse(
        UUID id,
        UUID locationId,
        String nickname,
        String openedBy,
        LocalDateTime openedAt,
        String closedBy,
        LocalDateTime closedAt,
        boolean open) {
}
