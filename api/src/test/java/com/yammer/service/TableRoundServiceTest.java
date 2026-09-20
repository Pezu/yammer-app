package com.yammer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointAssignmentEntity;
import com.yammer.repository.OrderPointAssignmentRepository;
import com.yammer.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The assignment is the sitting: what belongs to the table's current round, and what blocks leaving it. */
class TableRoundServiceTest {

    private final UUID table = UUID.randomUUID();
    private final OrderPointAssignmentRepository assignments = mock(OrderPointAssignmentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);

    private TableRoundService service() {
        return new TableRoundService(assignments, orders);
    }

    @Test
    void theRoundStartsWithTheFIRSTWaiterWhoTookTheTable() {
        LocalDateTime first = LocalDateTime.of(2026, 9, 12, 19, 0);
        when(assignments.findByOrderPointIdIn(List.of(table)))
                .thenReturn(List.of(assignment(first.plusHours(2)), assignment(first)));

        assertEquals(first, service().roundStart(table).orElseThrow());
    }

    @Test
    void anUnassignedTableIsNotInServiceAndHasNoRound() {
        when(assignments.countByOrderPointId(table)).thenReturn(0L);
        when(assignments.findByOrderPointIdIn(List.of(table))).thenReturn(List.of());

        assertFalse(service().inService(table));
        assertTrue(service().roundStart(table).isEmpty());
    }

    @Test
    void theBillHoldsThisRoundsOrdersAndDropsTheLastPartysSettledOnes() {
        LocalDateTime roundStart = LocalDateTime.of(2026, 9, 12, 21, 0);
        OrderEntity thisParty = order(roundStart.plusMinutes(10));
        // the previous party's paid orders are simply outside the window that is queried
        when(assignments.findByOrderPointIdIn(List.of(table))).thenReturn(List.of(assignment(roundStart)));
        when(orders.findByOrderPointIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(table, roundStart))
                .thenReturn(List.of(thisParty));
        when(orders.findUnsettledByOrderPointIdIn(List.of(table))).thenReturn(List.of(thisParty));

        assertEquals(List.of(thisParty.getId()),
                service().roundOrders(table).stream().map(OrderEntity::getId).toList());
    }

    @Test
    void moneyOwedFromAnEarlierRoundIsNeverHidden() {
        LocalDateTime roundStart = LocalDateTime.of(2026, 9, 12, 21, 0);
        OrderEntity stillOwed = order(roundStart.minusHours(3)); // left unpaid by the previous sitting
        when(assignments.findByOrderPointIdIn(List.of(table))).thenReturn(List.of(assignment(roundStart)));
        when(orders.findByOrderPointIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(table, roundStart))
                .thenReturn(List.of());
        when(orders.findUnsettledByOrderPointIdIn(List.of(table))).thenReturn(List.of(stillOwed));

        assertEquals(List.of(stillOwed.getId()),
                service().roundOrders(table).stream().map(OrderEntity::getId).toList());
        assertTrue(service().hasUnsettled(table)); // and it blocks leaving the table
    }

    @Test
    void aFullySettledTableCanBeLeft() {
        when(orders.findUnsettledByOrderPointIdIn(List.of(table))).thenReturn(List.of());

        assertFalse(service().hasUnsettled(table));
        assertEquals(Set.of(), service().pointsWithUnsettled(List.of(table)));
    }

    @Test
    void theDayCloseGuardNamesEveryTableThatOwes() {
        UUID other = UUID.randomUUID();
        OrderEntity owed = order(LocalDateTime.now());
        when(orders.findUnsettledByOrderPointIdIn(List.of(table, other))).thenReturn(List.of(owed));

        assertEquals(Set.of(table), service().pointsWithUnsettled(List.of(table, other)));
    }

    private OrderPointAssignmentEntity assignment(LocalDateTime at) {
        OrderPointAssignmentEntity a = new OrderPointAssignmentEntity();
        a.setId(UUID.randomUUID());
        a.setOrderPointId(table);
        a.setUserId(UUID.randomUUID());
        a.setAssignedAt(at);
        return a;
    }

    private OrderEntity order(LocalDateTime at) {
        OrderEntity o = new OrderEntity();
        o.setId(UUID.randomUUID());
        o.setOrderPointId(table);
        o.setCreatedAt(at);
        o.setStatus("ORDERED");
        return o;
    }

}
