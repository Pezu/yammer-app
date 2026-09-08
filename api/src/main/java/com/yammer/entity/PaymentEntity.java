package com.yammer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // ─── fiscal receipt (the on-prem bridge) ─────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "fiscal_status", nullable = false)
    private FiscalStatus fiscalStatus = FiscalStatus.NONE;

    @Column(name = "receipt_number")
    private String receiptNumber;

    /** When the fiscal RECEIPT was last pushed to the bridge (the sweeper's deadline start). */
    @Column(name = "fiscal_sent_at")
    private LocalDateTime fiscalSentAt;

    /**
     * Bridge device that first handled this payment's fiscal receipt. Once set, every
     * re-dispatch is routed to this device only — a different bridge has no de-dup record
     * of the receipt and could print it a second time.
     */
    @Column(name = "fiscal_device")
    private String fiscalDevice;

    /**
     * Operator verified an UNKNOWN receipt as NOT printed. The next RECEIPT frame carries
     * {@code clearIntent} so the bridge drops its print-intent and actually re-prints.
     * Cleared again on SUCCESS.
     */
    @Column(name = "fiscal_reprint_authorized", nullable = false)
    private boolean fiscalReprintAuthorized = false;
}
