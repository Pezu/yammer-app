package com.yammer.dto;

import com.yammer.entity.ConnectionType;
import com.yammer.entity.IntegrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IntegrationRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        String ip,
        @NotNull IntegrationType type,
        ConnectionType connection,
        /** Bridge device id for USB integrations (ignored for TCP). */
        String deviceId) {
}
