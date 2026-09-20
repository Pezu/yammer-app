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

@Entity
@Table(name = "order_point_assignment")
@Getter
@Setter
public class OrderPointAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * When this waiter took the table. The earliest active assignment dates the table's
     * current round — what used to be the table session's opened_at.
     */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;
}
