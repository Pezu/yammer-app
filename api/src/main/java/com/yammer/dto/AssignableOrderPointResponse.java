package com.yammer.dto;

import java.util.List;
import java.util.UUID;

/** One row of the assignment board: an order point plus who currently works it. */
public record AssignableOrderPointResponse(
        UUID id,
        String name,
        UUID typeId,
        boolean allowMultipleUsers,
        int assignedCount,
        boolean assignedToMe,
        List<String> assignedNames) {
}
