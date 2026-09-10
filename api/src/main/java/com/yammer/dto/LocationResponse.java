package com.yammer.dto;

import com.yammer.entity.LocationEntity;
import java.util.UUID;

public record LocationResponse(UUID id, String name, UUID clientId, boolean active, UUID qrTemplateId) {

    public static LocationResponse from(LocationEntity entity) {
        return new LocationResponse(entity.getId(), entity.getName(), entity.getClientId(), entity.isActive(),
                entity.getQrTemplateId());
    }
}
