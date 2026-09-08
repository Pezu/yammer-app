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
        UUID bridgeId,
        /** Name of the MOBILE row a register / printer is attached to (null otherwise). */
        String bridgeName,
        /** MOBILE rows: whether that bridge phone currently holds a live session. */
        Boolean online) {

    public static IntegrationResponse from(IntegrationEntity entity, String bridgeName, Boolean online) {
        return new IntegrationResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getIp(),
                entity.getType(),
                entity.getConnection(),
                entity.getDeviceId(),
                entity.getBridgeId(),
                bridgeName,
                online);
    }
}
