package com.yammer.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record LocationRequest(
        @NotBlank String name,
        // Required for SUPER (who choose the client); ignored for others (forced to own client).
        UUID clientId,
        // null = true on create / unchanged on update.
        Boolean active,
        // QR frame for the location's printed QR sheets; null = none (plain grid).
        UUID qrTemplateId) {
}
