package com.yammer.repository;

import com.yammer.entity.OrderPointAssignmentEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointAssignmentRepository extends JpaRepository<OrderPointAssignmentEntity, UUID> {

    List<OrderPointAssignmentEntity> findByUserId(UUID userId);

    List<OrderPointAssignmentEntity> findByOrderPointIdIn(Collection<UUID> orderPointIds);

    boolean existsByOrderPointIdAndUserId(UUID orderPointId, UUID userId);

    long countByOrderPointId(UUID orderPointId);

    void deleteByOrderPointIdAndUserId(UUID orderPointId, UUID userId);

    void deleteByOrderPointId(UUID orderPointId);
}
