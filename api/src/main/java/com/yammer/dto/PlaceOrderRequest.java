package com.yammer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Place an order at an order point (always ORDERED — the payment flow is not ported yet). */
public record PlaceOrderRequest(
        @NotNull UUID orderPointId,
        @NotEmpty @Valid List<OrderItemRequest> items) {
}
