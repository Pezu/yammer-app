package com.yammer.dto;

import com.yammer.entity.ProductEntity;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID locationId,
        String name,
        String description,
        UUID vatTypeId,
        String imageObject) {

    public static ProductResponse from(ProductEntity entity) {
        return new ProductResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVatTypeId(),
                entity.getImageObject());
    }
}
