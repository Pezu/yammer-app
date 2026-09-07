package com.yammer.repository;

import com.yammer.entity.PaymentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    /** Payments taken at one location's order points, newest first. */
    @Query("""
            select p from PaymentEntity p, OrderPointEntity op
            where p.orderPointId = op.id and op.locationId = :locationId
            order by p.createdAt desc
            """)
    List<PaymentEntity> findByLocationId(@Param("locationId") UUID locationId);
}
