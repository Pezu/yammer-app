package com.yammer.repository;

import com.yammer.entity.OrderEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtDesc(UUID orderPointId);

    List<OrderEntity> findByOrderPointIdOrderByCreatedAtAsc(UUID orderPointId);

    List<OrderEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<OrderEntity> findBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<OrderEntity> findBySessionIdIn(Collection<UUID> sessionIds);

    List<OrderEntity> findByOrderPointIdInOrderByCreatedAtDesc(Collection<UUID> orderPointIds);

    List<OrderEntity> findByOrderPointIdInAndStatusInOrderByCreatedAtDesc(
            Collection<UUID> orderPointIds, Collection<String> statuses);

    /** Highest order number issued for a client's locations (0 when none yet). */
    @Query("""
            select coalesce(max(o.orderNo), 0)
            from OrderEntity o, OrderPointEntity op, LocationEntity l
            where o.orderPointId = op.id and op.locationId = l.id and l.clientId = :clientId
            """)
    long maxOrderNoForClient(@Param("clientId") UUID clientId);
}
