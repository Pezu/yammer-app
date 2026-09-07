package com.yammer.repository;

import com.yammer.entity.CustomerSessionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSessionRepository extends JpaRepository<CustomerSessionEntity, UUID> {

    Optional<CustomerSessionEntity> findByToken(UUID token);

    List<CustomerSessionEntity> findByTableSessionIdInAndStatusOrderByCreatedAtAsc(
            Collection<UUID> tableSessionIds, String status);
}
