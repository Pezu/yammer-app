package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProductRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        String description,
        UUID vatTypeId,
        // Object-storage key from POST /menu/image; null = no image.
        String imageObject) {
}
