package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payment")
@Getter
@Setter
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_point_id", nullable = false)
    private UUID orderPointId;

    /** The table session this payment settled lines of. */
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tip = BigDecimal.ZERO;

    /** From the payment_type catalog; restricted to the order point's accepted set. */
    @Column(name = "payment_type_id")
    private UUID paymentTypeId;

    /** SUCCESS / PENDING (card, awaiting the softPOS callback) / FAILED. */
    @Column(nullable = false)
    private String status = "SUCCESS";

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
