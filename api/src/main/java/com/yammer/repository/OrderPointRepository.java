package com.yammer.repository;

import com.yammer.entity.OrderPointEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointRepository extends JpaRepository<OrderPointEntity, UUID> {

    List<OrderPointEntity> findByLocationIdOrderByName(UUID locationId);

    /** The points routed to any of these service stations. */
    List<OrderPointEntity> findByServiceOrderPointIdIn(Collection<UUID> serviceOrderPointIds);
}
