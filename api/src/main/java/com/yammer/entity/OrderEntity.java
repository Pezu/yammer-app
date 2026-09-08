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
@Table(name = "orders")
@Getter
@Setter
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Per-client sequence, issued when the order enters the flow (null while a DRAFT). */
    @Column(name = "order_no")
    private Long orderNo;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    /** The table session this order belongs to. */
    @Column(name = "session_id")
    private UUID sessionId;

    /** The customer device that placed this order (null for waiter orders). */
    @Column(name = "customer_session_id")
    private UUID customerSessionId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String status;
}
