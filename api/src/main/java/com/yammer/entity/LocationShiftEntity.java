package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A location's service day: opened and closed by a manager; at most one open at a time. */
@Entity
@Table(name = "location_shift")
@Getter
@Setter
public class LocationShiftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    /** The manager's own label for this day; optional. */
    @Column(name = "nickname")
    private String nickname;

    @Column(name = "opened_by")
    private String openedBy;

    /** Set in Java so it is readable straight after the insert; the DB default is the backstop. */
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
