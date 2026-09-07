package com.yammer.service;

import com.yammer.dto.ApprovalsResponse;
import com.yammer.entity.CustomerSessionEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.TableSessionEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.CustomerSessionRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.TableSessionRepository;
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
import lombok.RequiredArgsConstructor;
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

    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderPointRepository orderPointRepository;
    private final TableSessionRepository sessionRepository;
    private final CustomerSessionRepository customerSessionRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderReportService orderReportService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    /** Pending customer joins + APPROVAL orders across the caller's assigned tables. */
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
        List<TableSessionEntity> openSessions =
                sessionRepository.findByOrderPointIdInAndClosedAtIsNull(pointIds);
        Map<UUID, UUID> sessionToPoint = openSessions.stream()
                .collect(Collectors.toMap(TableSessionEntity::getId, TableSessionEntity::getOrderPointId));

        List<ApprovalsResponse.CustomerApprovalRow> customers = customerSessionRepository
                .findByTableSessionIdInAndStatusOrderByCreatedAtAsc(
                        sessionToPoint.keySet(), CustomerSessionEntity.PENDING)
                .stream()
                .map(cs -> {
                    UUID pointId = sessionToPoint.get(cs.getTableSessionId());
                    return new ApprovalsResponse.CustomerApprovalRow(
                            cs.getId(), pointId, pointNames.getOrDefault(pointId, "?"),
                            cs.getCreatedAt() == null
                                    ? null
                                    : cs.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
                })
                .toList();

        List<OrderEntity> approvalOrders = orderRepository.findBySessionIdIn(sessionToPoint.keySet())
                .stream()
                .filter(o -> "APPROVAL".equals(o.getStatus()))
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt))
                .toList();
        return new ApprovalsResponse(customers, orderReportService.assembleRows(approvalOrders, pointNames));
    }

    /** Approve or deny a customer device's join request (idempotent once decided). */
    public void decideCustomer(UUID customerSessionId, boolean approve) {
        UserEntity me = requireUser();
        CustomerSessionEntity cs = customerSessionRepository.findById(customerSessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Request not found: " + customerSessionId));
        TableSessionEntity ts = sessionRepository.findById(cs.getTableSessionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Request not found: " + customerSessionId));
        requireAssigned(me, ts.getOrderPointId());
        if (!CustomerSessionEntity.PENDING.equals(cs.getStatus())) {
            return;
        }
        cs.setStatus(approve ? CustomerSessionEntity.APPROVED : CustomerSessionEntity.DENIED);
        cs.setDecidedAt(LocalDateTime.now());
        cs.setDecidedBy(me.getUsername());
        customerSessionRepository.save(cs);
    }

    /** Approve (→ ORDERED, into the normal flow) or deny (delete) an APPROVAL order. */
    public void decideOrder(UUID orderId, boolean approve) {
        UserEntity me = requireUser();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        requireAssigned(me, order.getOrderPointId());
        if (!"APPROVAL".equals(order.getStatus())) {
            return;
        }
        if (approve) {
            order.setStatus("ORDERED");
            orderRepository.save(order);
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
