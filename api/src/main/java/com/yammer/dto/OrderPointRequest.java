package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/** Row edit — the type is fixed at creation (it's baked into the generated name). */
public record OrderPointRequest(
        @NotBlank String name,
        UUID selfPayTypeId,
        boolean allowMultipleUsers,
        List<UUID> paymentTypeIds,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId) {
}
