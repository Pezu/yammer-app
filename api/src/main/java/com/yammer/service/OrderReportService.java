package com.yammer.service;

import com.yammer.dto.OrderFilterOptionsResponse;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderPageResponse;
import com.yammer.dto.OrderReportRow;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The backoffice orders report: server-side paging + filters, and order administration. */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    /**
     * One page of the location's orders (newest first). Optional filters: order no,
     * order point, waiter (raw created_by value) and paid state (NOT / PAR / PAID).
     */
    @Transactional(readOnly = true)
    public OrderPageResponse listPaged(
            UUID locationId, int page, int size,
            Long orderNo, UUID orderPointId, String waiter, String paid) {
        accessGuard.requireAccessibleLocation(locationId);
        Map<UUID, String> pointNames = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));

        List<OrderEntity> orders = orderRepository
                .findByOrderPointIdInOrderByCreatedAtDesc(pointNames.keySet())
                .stream()
                .filter(o -> orderNo == null || o.getOrderNo() == orderNo.longValue())
                .filter(o -> orderPointId == null || orderPointId.equals(o.getOrderPointId()))
                .filter(o -> waiter == null || waiter.equals(o.getCreatedBy()))
                .toList();

        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Map<String, String> displayNames = displayNames(orders);

        List<OrderReportRow> rows = orders.stream()
                .map(o -> toRow(o, itemsByOrder.getOrDefault(o.getId(), List.of()),
                        pointNames.getOrDefault(o.getOrderPointId(), "?"), displayNames))
                .filter(r -> paid == null || paid.equals(r.paid()))
                .toList();

        int from = Math.min(page * size, rows.size());
        int to = Math.min(from + size, rows.size());
        return new OrderPageResponse(rows.subList(from, to), rows.size(), page, size);
    }

    /** Order-point and waiter option lists for the report's filter combos. */
    @Transactional(readOnly = true)
    public OrderFilterOptionsResponse filterOptions(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        List<OrderPointEntity> points = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .sorted((a, b) -> OrderPointService.compareNames(a.getName(), b.getName()))
                .toList();
        List<OrderEntity> orders = orderRepository
                .findByOrderPointIdInOrderByCreatedAtDesc(
                        points.stream().map(OrderPointEntity::getId).toList());
        Map<String, String> displayNames = displayNames(orders);
        List<OrderFilterOptionsResponse.WaiterOption> waiters = orders.stream()
                .map(OrderEntity::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .map(u -> new OrderFilterOptionsResponse.WaiterOption(u, displayNames.getOrDefault(u, u)))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        return new OrderFilterOptionsResponse(
                points.stream()
                        .map(p -> new OrderFilterOptionsResponse.PointOption(p.getId(), p.getName()))
                        .toList(),
                waiters);
    }

    /** Full rows (items + totals + paid state) for the given orders — report and kanban share this. */
    @Transactional(readOnly = true)
    public List<OrderReportRow> assembleRows(List<OrderEntity> orders, Map<UUID, String> pointNames) {
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        Map<String, String> displayNames = displayNames(orders);
        return orders.stream()
                .map(o -> toRow(o, itemsByOrder.getOrDefault(o.getId(), List.of()),
                        pointNames.getOrDefault(o.getOrderPointId(), "?"), displayNames))
                .toList();
    }

    /** Update an order's UNPAID item quantities (quantity ≤ 0 deletes the item). */
    public OrderReportRow updateItems(UUID orderId, List<OrderItemsUpdateRequest.ItemQuantity> updates) {
        OrderEntity order = requireAccessibleOrder(orderId);
        Map<UUID, Integer> qtyById = new HashMap<>();
        if (updates != null) {
            for (OrderItemsUpdateRequest.ItemQuantity u : updates) {
                qtyById.put(u.id(), u.quantity());
            }
        }
        for (OrderItemEntity item : orderItemRepository.findByOrderIdIn(List.of(orderId))) {
            if (item.getPaymentId() != null || !qtyById.containsKey(item.getId())) {
                continue; // paid items and untouched items are left as-is
            }
            int q = qtyById.get(item.getId());
            if (q <= 0) {
                orderItemRepository.delete(item);
            } else if (q != item.getQuantity()) {
                item.setQuantity(q);
                orderItemRepository.save(item);
            }
        }
        List<OrderItemEntity> remaining = orderItemRepository.findByOrderIdIn(List.of(orderId));
        String pointName = orderPointRepository.findById(order.getOrderPointId())
                .map(OrderPointEntity::getName)
                .orElse("?");
        return toRow(order, remaining, pointName, displayNames(List.of(order)));
    }

    /** Completely deletes an order (and its items). Only allowed when nothing is paid. */
    public void deleteOrder(UUID orderId) {
        requireAccessibleOrder(orderId);
        boolean anyPaid = orderItemRepository.findByOrderIdIn(List.of(orderId)).stream()
                .anyMatch(i -> i.getPaymentId() != null);
        if (anyPaid) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot delete an order that has paid items");
        }
        orderItemRepository.deleteAll(orderItemRepository.findByOrderIdIn(List.of(orderId)));
        orderRepository.deleteById(orderId);
    }

    /** Loads the order and tenant-gates it through its order point (cross-tenant → 404). */
    private OrderEntity requireAccessibleOrder(UUID orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        accessGuard.requireAccessibleOrderPoint(order.getOrderPointId());
        return order;
    }

    private Map<String, String> displayNames(List<OrderEntity> orders) {
        return userRepository
                .findByUsernameIn(orders.stream()
                        .map(OrderEntity::getCreatedBy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        UserEntity::getUsername,
                        u -> u.getName() != null ? u.getName() : u.getUsername()));
    }

    private OrderReportRow toRow(
            OrderEntity order, List<OrderItemEntity> items, String pointName,
            Map<String, String> displayNames) {
        List<OrderReportRow.Item> rowItems = items.stream()
                .map(i -> new OrderReportRow.Item(
                        i.getId(), i.getMenuItemId(), i.getName(),
                        i.getPrice(), i.getQuantity(), i.getPaymentId() != null))
                .toList();
        BigDecimal total = items.stream()
                .map(i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                        .multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long paidCount = items.stream().filter(i -> i.getPaymentId() != null).count();
        String paid = paidCount == 0 ? "NOT" : paidCount == items.size() ? "PAID" : "PAR";
        if (items.isEmpty()) {
            paid = "NOT";
        }
        return new OrderReportRow(
                order.getId(),
                order.getOrderNo(),
                order.getOrderPointId(),
                pointName,
                order.getCreatedBy() == null
                        ? "—"
                        : displayNames.getOrDefault(order.getCreatedBy(), order.getCreatedBy()),
                order.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                order.getStatus(),
                rowItems,
                total,
                paid);
    }
}
