package com.yammer.dto;

import com.yammer.entity.ConnectionType;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.IntegrationType;
import java.util.UUID;

public record IntegrationResponse(
        UUID id,
        UUID locationId,
        String name,
        String ip,
        IntegrationType type,
        ConnectionType connection,
        String deviceId) {

    public static IntegrationResponse from(IntegrationEntity entity) {
        return new IntegrationResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getIp(),
                entity.getType(),
                entity.getConnection(),
                entity.getDeviceId());
    }
}
