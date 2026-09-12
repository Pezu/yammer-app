package com.yammer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

/**
 * Place an order at an order point. With {@code paymentTypeId} the order is paid on the spot
 * (pay-as-you-order points such as bars): it is created DELIVERED, settled in full with that
 * payment type plus the optional {@code tip}, and never reaches the service board.
 */
public record PlaceOrderRequest(
        @NotNull UUID orderPointId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        UUID paymentTypeId,
        @PositiveOrZero BigDecimal tip) {
}
