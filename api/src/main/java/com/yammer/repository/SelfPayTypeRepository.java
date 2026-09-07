package com.yammer.repository;

import com.yammer.entity.SelfPayTypeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SelfPayTypeRepository extends JpaRepository<SelfPayTypeEntity, UUID> {

    boolean existsByTypeIgnoreCase(String type);
}
