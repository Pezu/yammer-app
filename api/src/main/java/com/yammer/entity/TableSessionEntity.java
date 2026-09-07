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

/** One "sitting" at a table: opened with the first assignee, closed when the last leaves. */
@Entity
@Table(name = "table_session")
@Getter
@Setter
public class TableSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    @Column(name = "opened_by")
    private String openedBy;

    @Column(name = "opened_at", insertable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
