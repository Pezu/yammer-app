package com.yammer.service;

import com.yammer.dto.OpenTableReportRow;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenTableReportService {

    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    /**
     * The location's tables currently in service (TABLE points only, no bars): who works
     * each one, since when, and what is still unpaid on it. A table is "open" while it has
     * an assignment — the assignment is the sitting — or while it still owes money.
     */
    public List<OpenTableReportRow> report(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        Map<UUID, String> typeById = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        Map<UUID, String> pointNames = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .filter(op -> "TABLE".equals(typeById.get(op.getTypeId())))
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        if (pointNames.isEmpty()) {
            return List.of();
        }

        List<OrderPointAssignmentEntity> assignments =
                assignmentRepository.findByOrderPointIdIn(pointNames.keySet());
        Map<UUID, List<OrderPointAssignmentEntity>> byPoint = assignments.stream()
                .collect(Collectors.groupingBy(OrderPointAssignmentEntity::getOrderPointId));
        Map<UUID, String> userNames = userRepository
                .findAllById(assignments.stream().map(OrderPointAssignmentEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId,
                        u -> u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName()));

        // what each table still owes, over its unsettled orders
        Map<UUID, BigDecimal> dueByPoint = new HashMap<>();
        List<OrderEntity> unsettled = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(pointNames.keySet())
                .stream()
                .filter(o -> !OrderService.DRAFT.equals(o.getStatus()))
                .toList();
        Map<UUID, UUID> orderToPoint = unsettled.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getOrderPointId));
        if (!orderToPoint.isEmpty()) {
            orderItemRepository.findByOrderIdInAndPaymentIdIsNull(orderToPoint.keySet())
                    .forEach(i -> dueByPoint.merge(orderToPoint.get(i.getOrderId()),
                            (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                                    .multiply(BigDecimal.valueOf(i.getQuantity())),
                            BigDecimal::add));
        }

        return pointNames.keySet().stream()
                .filter(id -> byPoint.containsKey(id) || dueByPoint.containsKey(id))
                .sorted((a, b) -> OrderPointService.compareNames(
                        pointNames.getOrDefault(a, "?"), pointNames.getOrDefault(b, "?")))
                .map(id -> {
                    List<OrderPointAssignmentEntity> here = byPoint.getOrDefault(id, List.of());
                    String who = here.isEmpty() ? "—" : here.stream()
                            .map(a -> userNames.getOrDefault(a.getUserId(), "?"))
                            .sorted()
                            .collect(Collectors.joining(", "));
                    LocalDateTime since = here.stream()
                            .map(OrderPointAssignmentEntity::getAssignedAt)
                            .filter(Objects::nonNull)
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    return new OpenTableReportRow(
                            id,
                            pointNames.getOrDefault(id, "?"),
                            who,
                            since == null ? null : since.atZone(ZoneId.systemDefault()).toInstant(),
                            dueByPoint.getOrDefault(id, BigDecimal.ZERO));
                })
                .toList();
    }
}
