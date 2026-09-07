package com.yammer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderPointsBatchRequest(
        @NotNull UUID locationId,
        @NotNull UUID typeId,
        @Min(1) int count,
        UUID selfPayTypeId,
        boolean allowMultipleUsers,
        List<UUID> paymentTypeIds,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId) {
}
