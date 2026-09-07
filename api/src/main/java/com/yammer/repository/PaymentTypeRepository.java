package com.yammer.repository;

import com.yammer.entity.PaymentTypeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTypeRepository extends JpaRepository<PaymentTypeEntity, UUID> {

    boolean existsByTypeIgnoreCase(String type);

    java.util.Optional<PaymentTypeEntity> findByTypeIgnoreCase(String type);
}
