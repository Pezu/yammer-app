package com.yammer.repository;

import com.yammer.entity.TableSessionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableSessionRepository extends JpaRepository<TableSessionEntity, UUID> {

    Optional<TableSessionEntity> findByOrderPointIdAndClosedAtIsNull(UUID orderPointId);

    List<TableSessionEntity> findByOrderPointIdInAndClosedAtIsNull(Collection<UUID> orderPointIds);
}
