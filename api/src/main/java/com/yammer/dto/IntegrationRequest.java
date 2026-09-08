package com.yammer.dto;

import com.yammer.entity.ConnectionType;
import com.yammer.entity.IntegrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record IntegrationRequest(
        @NotNull UUID locationId,
        @NotBlank String name,
        /** TCP: the device's LAN address. */
        String ip,
        @NotNull IntegrationType type,
        /** TCP or MOBILE (attached to a bridge phone). */
        ConnectionType connection,
        /** MOBILE: the phone's device id (from the connected-bridges list). */
        String deviceId,
        /** MOBILE: the phone's name, remembered for display while it is offline. */
        String deviceName) {
}
