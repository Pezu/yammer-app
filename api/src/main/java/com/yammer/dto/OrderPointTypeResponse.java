package com.yammer.dto;

import com.yammer.entity.OrderPointTypeEntity;
import java.util.UUID;

public record OrderPointTypeResponse(UUID id, String type) {

    public static OrderPointTypeResponse from(OrderPointTypeEntity entity) {
        return new OrderPointTypeResponse(entity.getId(), entity.getType());
    }
}
