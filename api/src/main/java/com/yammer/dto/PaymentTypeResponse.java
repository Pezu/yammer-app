package com.yammer.dto;

import com.yammer.entity.PaymentTypeEntity;
import java.util.UUID;

public record PaymentTypeResponse(UUID id, String type) {

    public static PaymentTypeResponse from(PaymentTypeEntity entity) {
        return new PaymentTypeResponse(entity.getId(), entity.getType());
    }
}
