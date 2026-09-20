package com.yammer.repository;

import com.yammer.entity.OrderEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    /** Every order rung up during one service day (the shift-scoped dashboard). */
    List<OrderEntity> findByShiftId(UUID shiftId);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtDesc(UUID orderPointId);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtAsc(UUID orderPointId);

    List<OrderEntity> findByOrderPointIdInOrderByCreatedAtDesc(Collection<UUID> orderPointIds);

    /** A table's orders since its round started (rides the (order_point_id, created_at) index). */
    List<OrderEntity> findByOrderPointIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            UUID orderPointId, java.time.LocalDateTime from);

    /**
     * Billable orders at these tables that still carry an unpaid line — what a table owes,
     * whatever round it came from. DRAFT/CANCELED mirror {@code OrderService.NOT_BILLABLE}.
     */
    @Query("""
            select distinct o from OrderEntity o, OrderItemEntity i
            where i.orderId = o.id and i.paymentId is null
              and o.orderPointId in :orderPointIds
              and o.status not in ('DRAFT', 'CANCELED')
            order by o.createdAt asc
            """)
    List<OrderEntity> findUnsettledByOrderPointIdIn(@Param("orderPointIds") Collection<UUID> orderPointIds);

    List<OrderEntity> findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
            Collection<UUID> orderPointIds, Collection<String> statuses);

    /** Orders placed at one location's points in [from, to). */
    @Query("""
            select o from OrderEntity o, OrderPointEntity op
            where o.orderPointId = op.id and op.locationId = :locationId
              and o.createdAt >= :from and o.createdAt < :to
            order by o.createdAt asc
            """)
    List<OrderEntity> findByLocationIdAndCreatedAtBetween(
            @Param("locationId") UUID locationId, @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    /** Highest order number issued for a client's locations (0 when none yet). */
    @Query("""
            select coalesce(max(o.orderNo), 0)
            from OrderEntity o, OrderPointEntity op, LocationEntity l
            where o.orderPointId = op.id and op.locationId = l.id and l.clientId = :clientId
            """)
    long maxOrderNoForClient(@Param("clientId") UUID clientId);
}
