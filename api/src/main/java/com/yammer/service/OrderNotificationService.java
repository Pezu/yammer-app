package com.yammer.service;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.OrderPointTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.event.OrderChangedEvent;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderPointTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.ws.OrderWsHandler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes order events over WebSocket to the users whose service board shows the order.
 * The routing mirrors {@link OrderPointAssignmentService#serviceBoard()}: the order's point
 * routes to its SERVICE station (or is one itself); a user sees that station when it is
 * among their assigned stations, or — with no station assigned at all — when it is in
 * their home location (the single-station fallback).
 *
 * <p>Triggered by {@link OrderChangedEvent} AFTER_COMMIT and asynchronously, so screens
 * never hear about an order that later rolls back and the push never delays the request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {

    private final OrderPointRepository orderPointRepository;
    private final OrderPointTypeRepository orderPointTypeRepository;
    private final OrderPointAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final OrderWsHandler wsHandler;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        try {
            List<String> usernames = recipients(event.order());
            if (usernames.isEmpty()) {
                return;
            }
            String payload = "{\"type\":\"" + event.type() + "\",\"orderId\":\"" + event.order().getId() + "\"}";
            wsHandler.sendToUsers(usernames, payload);
        } catch (Exception e) {
            // a failed push must never break the order operation
            log.warn("Failed to push {} for order {}: {}", event.type(), event.order().getId(), e.getMessage());
        }
    }

    private List<String> recipients(OrderEntity order) {
        OrderPointEntity point = orderPointRepository.findById(order.getOrderPointId()).orElse(null);
        if (point == null) {
            return List.of();
        }
        UUID stationId = point.getServiceOrderPointId() != null ? point.getServiceOrderPointId() : point.getId();
        Map<UUID, String> typeById = orderPointTypeRepository.findAll().stream()
                .collect(Collectors.toMap(OrderPointTypeEntity::getId, OrderPointTypeEntity::getType));
        List<UserEntity> candidates = userRepository.findByLocationId(point.getLocationId());
        if (candidates.isEmpty()) {
            return List.of();
        }
        // every station assignment of the location's users, in one query
        Map<UUID, Set<UUID>> stationsByUser = assignmentRepository
                .findByUserIdIn(candidates.stream().map(UserEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderPointAssignmentEntity::getUserId,
                        Collectors.mapping(OrderPointAssignmentEntity::getOrderPointId, Collectors.toSet())));
        Set<UUID> servicePoints = orderPointRepository.findByLocationIdOrderByName(point.getLocationId()).stream()
                .filter(p -> "SERVICE".equals(typeById.get(p.getTypeId())))
                .map(OrderPointEntity::getId)
                .collect(Collectors.toSet());
        return candidates.stream()
                .filter(u -> {
                    Set<UUID> mine = stationsByUser.getOrDefault(u.getId(), Set.of()).stream()
                            .filter(servicePoints::contains)
                            .collect(Collectors.toSet());
                    return mine.isEmpty() ? servicePoints.contains(stationId) : mine.contains(stationId);
                })
                .map(UserEntity::getUsername)
                .toList();
    }
}
