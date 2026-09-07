package com.yammer.dto;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        Long orderNo,
        UUID orderPointId,
        String orderPointName,
        String createdBy,
        Instant createdAt,
        String status,
        List<OrderItemResponse> items,
        BigDecimal total) {

    public record OrderItemResponse(UUID id, UUID menuItemId, String name, BigDecimal price, int quantity) {
        public static OrderItemResponse from(OrderItemEntity e) {
            return new OrderItemResponse(e.getId(), e.getMenuItemId(), e.getName(), e.getPrice(), e.getQuantity());
        }
    }

    public static OrderResponse from(OrderEntity order, List<OrderItemEntity> items, String orderPointName) {
        BigDecimal total = items.stream()
                .map(i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getOrderPointId(),
                orderPointName,
                order.getCreatedBy(),
                order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                order.getStatus(),
                items.stream().map(OrderItemResponse::from).toList(),
                total);
    }
}
