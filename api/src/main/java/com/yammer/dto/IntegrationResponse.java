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
        String deviceId,
        String deviceName,
        /** Connection MOBILE: whether the attached phone currently holds a live bridge session. */
        Boolean online) {

    public static IntegrationResponse from(IntegrationEntity entity, Boolean online) {
        return new IntegrationResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getIp(),
                entity.getType(),
                entity.getConnection(),
                entity.getDeviceId(),
                entity.getDeviceName(),
                online);
    }
}
