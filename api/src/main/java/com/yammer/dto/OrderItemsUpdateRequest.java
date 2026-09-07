package com.yammer.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Quantity updates for an order's UNPAID items (quantity ≤ 0 deletes the item). */
public record OrderItemsUpdateRequest(List<ItemQuantity> items) {

    public record ItemQuantity(@NotNull UUID id, int quantity) {
    }
}
