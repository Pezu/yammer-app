package com.yammer.dto;

import com.yammer.entity.SelfPayTypeEntity;
import java.util.UUID;

public record SelfPayTypeResponse(UUID id, String type) {

    public static SelfPayTypeResponse from(SelfPayTypeEntity entity) {
        return new SelfPayTypeResponse(entity.getId(), entity.getType());
    }
}
