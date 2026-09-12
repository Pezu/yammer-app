package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/** Row edit — the type is fixed at creation (it's baked into the generated name). */
public record OrderPointRequest(
        @NotBlank String name,
        // free label under the name on the waiter's tiles; blank = none
        String nickname,
        // ALLOW / CONFIRM / DISALLOW; null = unchanged
        String selfOrderMode,
        UUID selfPayTypeId,
        boolean allowMultipleUsers,
        boolean keepOpen,
        List<UUID> paymentTypeIds,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId) {
}
