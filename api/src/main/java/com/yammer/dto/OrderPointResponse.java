package com.yammer.dto;

import com.yammer.entity.OrderPointEntity;
import java.util.List;
import java.util.UUID;

public record OrderPointResponse(
        UUID id,
        UUID locationId,
        String name,
        String nickname,
        String selfOrderMode,
        java.math.BigDecimal discountPercent,
        UUID typeId,
        UUID selfPayTypeId,
        boolean allowMultipleUsers,
        boolean keepOpen,
        List<UUID> paymentTypeIds,
        UUID menuId,
        UUID serviceOrderPointId,
        UUID printerId,
        UUID cashRegisterId) {

    public static OrderPointResponse from(OrderPointEntity entity) {
        return new OrderPointResponse(
                entity.getId(),
                entity.getLocationId(),
                entity.getName(),
                entity.getNickname(),
                entity.getSelfOrderMode(),
                entity.getDiscountPercent(),
                entity.getTypeId(),
                entity.getSelfPayTypeId(),
                entity.isAllowMultipleUsers(),
                entity.isKeepOpen(),
                entity.getPaymentTypeIds(),
                entity.getMenuId(),
                entity.getServiceOrderPointId(),
                entity.getPrinterId(),
                entity.getCashRegisterId());
    }
}
