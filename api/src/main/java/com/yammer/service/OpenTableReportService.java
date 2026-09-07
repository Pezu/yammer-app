package com.yammer.service;

import com.yammer.dto.OpenTableReportRow;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.TableSessionEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.TableSessionRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
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

    private final TableSessionRepository sessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    /** The location's OPEN table sessions (TABLE points only, no bars) with each one's outstanding (unpaid) amount. */
    public List<OpenTableReportRow> report(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        Map<UUID, String> typeById = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        Map<UUID, String> pointNames = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .filter(op -> "TABLE".equals(typeById.get(op.getTypeId())))
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        List<TableSessionEntity> sessions =
                sessionRepository.findByOrderPointIdInAndClosedAtIsNull(pointNames.keySet());

        List<OrderEntity> orders =
                orderRepository.findBySessionIdIn(sessions.stream().map(TableSessionEntity::getId).toList());
        Map<UUID, UUID> orderToSession = orders.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getSessionId));
        Map<UUID, BigDecimal> dueBySession = orderItemRepository
                .findByOrderIdInAndPaymentIdIsNull(orderToSession.keySet())
                .stream()
                .collect(Collectors.groupingBy(
                        i -> orderToSession.get(i.getOrderId()),
                        Collectors.mapping(
                                i -> (i.getPrice() == null ? BigDecimal.ZERO : i.getPrice())
                                        .multiply(BigDecimal.valueOf(i.getQuantity())),
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<String, String> waiterNames = userRepository
                .findByUsernameIn(sessions.stream()
                        .map(TableSessionEntity::getOpenedBy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        UserEntity::getUsername,
                        u -> u.getName() != null ? u.getName() : u.getUsername()));

        return sessions.stream()
                .sorted((a, b) -> OrderPointService.compareNames(
                        pointNames.getOrDefault(a.getOrderPointId(), "?"),
                        pointNames.getOrDefault(b.getOrderPointId(), "?")))
                .map(s -> new OpenTableReportRow(
                        s.getId(),
                        pointNames.getOrDefault(s.getOrderPointId(), "?"),
                        s.getOpenedBy() == null
                                ? "—"
                                : waiterNames.getOrDefault(s.getOpenedBy(), s.getOpenedBy()),
                        s.getOpenedAt() == null
                                ? null
                                : s.getOpenedAt().atZone(ZoneId.systemDefault()).toInstant(),
                        dueBySession.getOrDefault(s.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
