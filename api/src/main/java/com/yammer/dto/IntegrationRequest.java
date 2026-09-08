package com.yammer.dto;

import com.yammer.entity.ConnectionType;
import com.yammer.entity.IntegrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IntegrationRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        /** TCP registers / printers: the LAN address. */
        String ip,
        @NotNull IntegrationType type,
        /** Registers / printers only: TCP or MOBILE. */
        ConnectionType connection,
        /** MOBILE rows only: the bridge phone's device id. */
        String deviceId,
        /** Registers / printers with connection MOBILE: the MOBILE integration to attach to. */
        UUID bridgeId) {
}
