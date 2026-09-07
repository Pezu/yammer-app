package com.yammer.service;

import com.yammer.dto.AssignableOrderPointResponse;
import com.yammer.dto.OrderPointResponse;
import com.yammer.dto.OrderReportRow;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.TableSessionEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.TableSessionRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderPointAssignmentService {

    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final OrderRepository orderRepository;
    private final TableSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;
    private final AccessGuard accessGuard;
    private final OrderService orderService;
    private final OrderReportService orderReportService;

    /**
     * Service kanban board: undelivered (ORDERED/READY) orders from the points routed
     * to the caller's assigned SERVICE stations — plus orders placed on the stations
     * themselves (a station with no downstream routing implicitly serves its own).
     * A caller with no assigned station falls back to ALL SERVICE points of their home
     * location, so single-station setups need zero assignment config.
     */
    @Transactional(readOnly = true)
    public List<OrderReportRow> serviceBoard() {
        UserEntity me = requireUser();
        Map<UUID, String> typeById = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        List<OrderPointEntity> stations = orderPointRepository
                .findAllById(assignmentRepository.findByUserId(me.getId()).stream()
                        .map(OrderPointAssignmentEntity::getOrderPointId)
                        .toList())
                .stream()
                .filter(p -> "SERVICE".equals(typeById.get(p.getTypeId())))
                .toList();
        if (stations.isEmpty() && me.getLocationId() != null) {
            stations = orderPointRepository.findByLocationIdOrderByName(me.getLocationId()).stream()
                    .filter(p -> "SERVICE".equals(typeById.get(p.getTypeId())))
                    .toList();
        }
        if (stations.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new LinkedHashMap<>();
        stations.forEach(sp -> names.put(sp.getId(), sp.getName()));
        orderPointRepository.findByServiceOrderPointIdIn(List.copyOf(names.keySet()))
                .forEach(src -> names.put(src.getId(), src.getName()));
        List<OrderEntity> orders = orderRepository.findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
                names.keySet(), OrderService.BOARD_STATUSES);
        return orderReportService.assembleRows(orders, names);
    }

    /** The order points assigned to the current user, naturally ordered. */
    @Transactional(readOnly = true)
    public List<OrderPointResponse> myAssigned() {
        UserEntity me = requireUser();
        List<UUID> pointIds = assignmentRepository.findByUserId(me.getId()).stream()
                .map(OrderPointAssignmentEntity::getOrderPointId)
                .toList();
        return orderPointRepository.findAllById(pointIds).stream()
                .sorted((a, b) -> OrderPointService.compareNames(a.getName(), b.getName()))
                .map(OrderPointResponse::from)
                .toList();
    }

    /** The location's order points with their current assignment state (the picker board). */
    @Transactional(readOnly = true)
    public List<AssignableOrderPointResponse> assignable(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        UserEntity me = requireUser();

        List<OrderPointEntity> points = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .sorted((a, b) -> OrderPointService.compareNames(a.getName(), b.getName()))
                .toList();
        List<OrderPointAssignmentEntity> assignments =
                assignmentRepository.findByOrderPointIdIn(points.stream().map(OrderPointEntity::getId).toList());

        Map<UUID, List<OrderPointAssignmentEntity>> byPoint = assignments.stream()
                .collect(Collectors.groupingBy(OrderPointAssignmentEntity::getOrderPointId));
        Map<UUID, String> displayNames = userRepository
                .findAllById(assignments.stream().map(OrderPointAssignmentEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        UserEntity::getId,
                        u -> u.getName() != null ? u.getName() : u.getUsername()));

        return points.stream()
                .map(point -> {
                    List<OrderPointAssignmentEntity> here = byPoint.getOrDefault(point.getId(), List.of());
                    return new AssignableOrderPointResponse(
                            point.getId(),
                            point.getName(),
                            point.getTypeId(),
                            point.isAllowMultipleUsers(),
                            here.size(),
                            here.stream().anyMatch(a -> a.getUserId().equals(me.getId())),
                            here.stream().map(a -> displayNames.getOrDefault(a.getUserId(), "?")).toList());
                })
                .toList();
    }

    /**
     * Assign the current user to the point (idempotent). Single-user points refuse a
     * second user. Assignment opens the table session (or joins the one already open).
     */
    public void assign(UUID orderPointId) {
        OrderPointEntity point = accessGuard.requireAccessibleOrderPoint(orderPointId);
        UserEntity me = requireUser();
        if (!assignmentRepository.existsByOrderPointIdAndUserId(orderPointId, me.getId())) {
            if (!point.isAllowMultipleUsers() && assignmentRepository.countByOrderPointId(orderPointId) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order point is already taken");
            }
            OrderPointAssignmentEntity assignment = new OrderPointAssignmentEntity();
            assignment.setOrderPointId(orderPointId);
            assignment.setUserId(me.getId());
            assignmentRepository.save(assignment);
        }
        if (sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId).isEmpty()) {
            TableSessionEntity session = new TableSessionEntity();
            session.setOrderPointId(orderPointId);
            session.setOpenedBy(me.getUsername());
            sessionRepository.save(session);
        }
    }

    /**
     * Remove the current user's assignment (no-op if not assigned). Refused (409)
     * whenever the point has an OPEN session — an open table is left only by closing
     * it ({@link #closeTable}), which clears the assignments itself.
     */
    public void unassign(UUID orderPointId) {
        accessGuard.requireAccessibleOrderPoint(orderPointId);
        UserEntity me = requireUser();
        if (!assignmentRepository.existsByOrderPointIdAndUserId(orderPointId, me.getId())) {
            return;
        }
        if (sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Table session is open — close the table to free it");
        }
        assignmentRepository.deleteByOrderPointIdAndUserId(orderPointId, me.getId());
    }

    /**
     * The ONLY way a table session ends: close it once everything is paid (409 while
     * unpaid lines remain) and free the table by clearing every assignment on it.
     */
    public void closeTable(UUID orderPointId) {
        accessGuard.requireAccessibleOrderPoint(orderPointId);
        UserEntity me = requireUser();
        TableSessionEntity session = sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No open session"));
        if (orderService.sessionHasUnpaid(session.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Table is not fully settled");
        }
        session.setClosedBy(me.getUsername());
        session.setClosedAt(LocalDateTime.now());
        sessionRepository.save(session);
        assignmentRepository.deleteByOrderPointId(orderPointId);
    }

    private UserEntity requireUser() {
        return userRepository.findByUsername(currentUser.require().username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }
}
