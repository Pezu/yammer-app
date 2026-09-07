package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;

/** A kanban status move (ORDERED / READY / DELIVERED / CANCELED). */
public record OrderStatusRequest(@NotBlank String status) {
}
