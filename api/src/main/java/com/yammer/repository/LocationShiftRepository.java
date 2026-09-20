package com.yammer.repository;

import com.yammer.entity.LocationShiftEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationShiftRepository extends JpaRepository<LocationShiftEntity, UUID> {

    Optional<LocationShiftEntity> findByLocationIdAndClosedAtIsNull(UUID locationId);

    Optional<LocationShiftEntity> findFirstByLocationIdOrderByOpenedAtDesc(UUID locationId);

    /** The location's service days, newest first (the backoffice Shifts page). */
    List<LocationShiftEntity> findByLocationIdOrderByOpenedAtDesc(UUID locationId);
}
