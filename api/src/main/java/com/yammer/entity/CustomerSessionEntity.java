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

/**
 * A customer device joined to an OPEN table session. The {@code token} lives in the
 * customer's browser so a re-scan resumes the same (approved) session; everything
 * dies with the table session — a closed table invalidates all its customer sessions.
 */
@Entity
@Table(name = "customer_session")
@Getter
@Setter
public class CustomerSessionEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String DENIED = "DENIED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "table_session_id", nullable = false)
    private UUID tableSessionId;

    /** Generated in code (not by the DB default) so it can be returned without a refresh. */
    @Column(nullable = false, updatable = false)
    private UUID token = UUID.randomUUID();

    @Column(nullable = false)
    private String status = PENDING;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;
}
