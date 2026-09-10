package com.yammer.repository;

import com.yammer.entity.QrTemplateEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QrTemplateRepository extends JpaRepository<QrTemplateEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);
}
