package com.yammer.service;

import com.yammer.dto.ApprovalsResponse;
import com.yammer.entity.CustomerSessionEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.CustomerSessionRepository;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.yammer.event.OrderChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The waiter's approval surface: customer devices joining their tables, customer
 * orders awaiting confirmation, and the per-table self-order mode. Everything is
 * scoped to tables the CALLER is assigned to.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalService {

    private static final Set<String> SELF_ORDER_MODES = Set.of("ALLOW", "CONFIRM", "DISALLOW");

    private final ApplicationEventPublisher eventPublisher;
    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderPointRepository orderPointRepository;
    private final LocationRepository locationRepository;
    private final TableRoundService roundService;
    private final CustomerSessionRepository customerSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReportService orderReportService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    /** Pending customer joins + DRAFT orders across the caller's assigned tables. */
    @Transactional(readOnly = true)
    public ApprovalsResponse list() {
        UserEntity me = requireUser();
        List<UUID> pointIds = assignmentRepository.findByUserId(me.getId()).stream()
                .map(OrderPointAssignmentEntity::getOrderPointId)
                .toList();
        if (pointIds.isEmpty()) {
            return new ApprovalsResponse(List.of(), List.of());
        }
        Map<UUID, String> pointNames = orderPointRepository.findAllById(pointIds).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        // Only the round running now: a request from the previous party is dead with it.
        Map<UUID, LocalDateTime> roundStart = pointIds.stream()
                .flatMap(id -> roundService.roundStart(id).map(at -> Map.entry(id, at)).stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<ApprovalsResponse.CustomerApprovalRow> customers = customerSessionRepository
                .findByOrderPointIdInAndStatusOrderByCreatedAtAsc(pointIds, CustomerSessionEntity.PENDING)
                .stream()
                .filter(cs -> inCurrentRound(cs.getCreatedAt(), roundStart.get(cs.getOrderPointId())))
                .map(cs -> new ApprovalsResponse.CustomerApprovalRow(
                        cs.getId(), cs.getOrderPointId(),
                        pointNames.getOrDefault(cs.getOrderPointId(), "?"),
                        cs.getCreatedAt() == null
                                ? null
                                : cs.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()))
                .toList();

        List<OrderEntity> approvalOrders = orderRepository.findByOrderPointIdInOrderByCreatedAtDesc(pointIds)
                .stream()
                .filter(o -> OrderService.DRAFT.equals(o.getStatus()))
                .filter(o -> inCurrentRound(o.getCreatedAt(), roundStart.get(o.getOrderPointId())))
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt))
                .toList();
        return new ApprovalsResponse(customers, orderReportService.assembleRows(approvalOrders, pointNames));
    }

    /** Whether something created at {@code at} belongs to a round that started at {@code start}. */
    private static boolean inCurrentRound(LocalDateTime at, LocalDateTime start) {
        return start != null && (at == null || !at.isBefore(start));
    }

    /** Approve or deny a customer device's join request (idempotent once decided). */
    public void decideCustomer(UUID customerSessionId, boolean approve) {
        UserEntity me = requireUser();
        CustomerSessionEntity cs = customerSessionRepository.findById(customerSessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Request not found: " + customerSessionId));
        requireAssigned(me, cs.getOrderPointId());
        if (!CustomerSessionEntity.PENDING.equals(cs.getStatus())) {
            return;
        }
        cs.setStatus(approve ? CustomerSessionEntity.APPROVED : CustomerSessionEntity.DENIED);
        cs.setDecidedAt(LocalDateTime.now());
        cs.setDecidedBy(me.getUsername());
        customerSessionRepository.save(cs);
    }

    /** Approve (number issued, → ORDERED, into the normal flow) or deny (delete) a DRAFT order. */
    public void decideOrder(UUID orderId, boolean approve) {
        UserEntity me = requireUser();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        requireAssigned(me, order.getOrderPointId());
        if (!OrderService.DRAFT.equals(order.getStatus())) {
            return;
        }
        if (approve) {
            UUID clientId = orderPointRepository.findById(order.getOrderPointId())
                    .flatMap(op -> locationRepository.findById(op.getLocationId()))
                    .map(l -> l.getClientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
            order.setOrderNo(orderRepository.maxOrderNoForClient(clientId) + 1); // numbered on entry
            order.setStatus("ORDERED");
            eventPublisher.publishEvent(new OrderChangedEvent(orderRepository.save(order), "ORDER_CREATED"));
        } else {
            orderItemRepository.deleteAll(orderItemRepository.findByOrderIdIn(List.of(orderId)));
            orderRepository.delete(order);
        }
    }

    /** Set the table's self-order mode (ALLOW / CONFIRM / DISALLOW) — assigned users only. */
    public void setSelfOrderMode(UUID orderPointId, String mode) {
        UserEntity me = requireUser();
        requireAssigned(me, orderPointId);
        String wanted = mode == null ? "" : mode.trim().toUpperCase();
        if (!SELF_ORDER_MODES.contains(wanted)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown self-order mode: " + mode);
        }
        OrderPointEntity point = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId));
        point.setSelfOrderMode(wanted);
        orderPointRepository.save(point);
    }

    private void requireAssigned(UserEntity me, UUID orderPointId) {
        if (!assignmentRepository.existsByOrderPointIdAndUserId(orderPointId, me.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to this table");
        }
    }

    private UserEntity requireUser() {
        return userRepository.findByUsername(currentUser.require().username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
