package com.yammer.repository;

import com.yammer.entity.OrderPointTypeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPointTypeRepository extends JpaRepository<OrderPointTypeEntity, UUID> {

    boolean existsByTypeIgnoreCase(String type);
}
