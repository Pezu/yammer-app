package com.yammer.service;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A table's current <em>round</em> — the party sitting at it — without a session row.
 *
 * <p>The assignment is the sitting: a table is in service while someone is assigned to it,
 * and the round started when the first of them took it. Leaving a table is only allowed with
 * nothing left to settle, so un-assigning is exactly where one party ends and the next
 * begins — the job {@code table_session.closed_at} used to do.
 *
 * <p>One deliberate asymmetry: the <em>paid</em> side of a bill is scoped to the round, but
 * unsettled lines never are. If a table somehow loses its assignment with money outstanding,
 * that money must stay visible and payable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableRoundService {

    private final OrderPointAssignmentRepository assignmentRepository;
    private final OrderRepository orderRepository;

    /** Whether anyone works this table — the gate on taking orders at it. */
    public boolean inService(UUID orderPointId) {
        return assignmentRepository.countByOrderPointId(orderPointId) > 0;
    }

    /** When the current round began; empty when nobody is assigned. */
    public Optional<LocalDateTime> roundStart(UUID orderPointId) {
        return assignmentRepository.findByOrderPointIdIn(List.of(orderPointId)).stream()
                .map(OrderPointAssignmentEntity::getAssignedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());
    }

    /**
     * The orders a table's bill is made of, oldest first: everything since the round started,
     * plus anything older that is still unpaid.
     */
    public List<OrderEntity> roundOrders(UUID orderPointId) {
        Map<UUID, OrderEntity> byId = new LinkedHashMap<>();
        roundStart(orderPointId).ifPresent(from -> orderRepository
                .findByOrderPointIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(orderPointId, from)
                .forEach(o -> byId.put(o.getId(), o)));
        unsettledOrders(orderPointId).forEach(o -> byId.put(o.getId(), o));
        return byId.values().stream()
                .sorted(Comparator.comparing(OrderEntity::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /** Billable orders at the table with at least one unpaid line, oldest first. */
    public List<OrderEntity> unsettledOrders(UUID orderPointId) {
        return orderRepository.findUnsettledByOrderPointIdIn(List.of(orderPointId));
    }

    /** Whether the table still owes something — the guard on leaving it, and on closing the day. */
    public boolean hasUnsettled(UUID orderPointId) {
        return !unsettledOrders(orderPointId).isEmpty();
    }

    /** Which of these tables still owe something, in one query (the day-close guard). */
    public Set<UUID> pointsWithUnsettled(Collection<UUID> orderPointIds) {
        if (orderPointIds.isEmpty()) {
            return Set.of();
        }
        return orderRepository.findUnsettledByOrderPointIdIn(orderPointIds).stream()
                .map(OrderEntity::getOrderPointId)
                .collect(Collectors.toSet());
    }
}
