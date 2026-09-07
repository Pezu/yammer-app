package com.yammer.repository;

import com.yammer.entity.ProductEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    List<ProductEntity> findByLocationIdOrderByName(UUID locationId);

    /** Listing order: last introduced first. */
    List<ProductEntity> findByLocationIdOrderByCreatedAtDesc(UUID locationId);
}
