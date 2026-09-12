package com.yammer.repository;

import com.yammer.entity.PaymentEntity;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {

    /** Payments taken at one location's order points, newest first. */
    @Query("""
            select p from PaymentEntity p, OrderPointEntity op
            where p.orderPointId = op.id and op.locationId = :locationId
            order by p.createdAt desc
            """)
    List<PaymentEntity> findByLocationId(@Param("locationId") UUID locationId);

    /** Payments taken at one location's points in [from, to). */
    @Query("""
            select p from PaymentEntity p, OrderPointEntity op
            where p.orderPointId = op.id and op.locationId = :locationId
              and p.createdAt >= :from and p.createdAt < :to
            order by p.createdAt asc
            """)
    List<PaymentEntity> findByLocationIdAndCreatedAtBetween(
            @Param("locationId") UUID locationId, @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    // ─── fiscal status transitions ──────────────────────────────────────────────
    // All guarded, atomic UPDATEs: SUCCESS is terminal (a printed receipt cannot
    // un-print), an ERROR result only applies while the attempt is in flight, and a
    // retry only re-arms a FAILED payment. Callers branch on the returned row count.

    /** Sweeper: PENDING past the deadline → FAILED (a concurrent SUCCESS makes the row not match). */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.FAILED "
            + "where p.fiscalStatus = com.yammer.entity.FiscalStatus.PENDING "
            + "and ((p.fiscalSentAt is not null and p.fiscalSentAt < :cutoff) "
            + "or (p.fiscalSentAt is null and p.createdAt < :cutoff))")
    int failStaleFiscalPending(@Param("cutoff") LocalDateTime cutoff);

    /** Result OK: reach SUCCESS from any non-terminal state; keep an existing receipt number. */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.SUCCESS, "
            + "p.receiptNumber = coalesce(:receiptNumber, p.receiptNumber), "
            + "p.fiscalReprintAuthorized = false "
            + "where p.id = :id and p.fiscalStatus <> com.yammer.entity.FiscalStatus.SUCCESS")
    int markFiscalSuccess(@Param("id") UUID id, @Param("receiptNumber") String receiptNumber);

    /** Result ERROR: only a PENDING (in-flight) attempt may fail — never a SUCCESS or UNKNOWN. */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.FAILED "
            + "where p.id = :id and p.fiscalStatus = com.yammer.entity.FiscalStatus.PENDING")
    int markFiscalFailedFromPending(@Param("id") UUID id);

    /** Result UNKNOWN (ambiguous print): needs operator verification; SUCCESS stays terminal. */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.UNKNOWN "
            + "where p.id = :id and p.fiscalStatus in (com.yammer.entity.FiscalStatus.PENDING, "
            + "com.yammer.entity.FiscalStatus.FAILED)")
    int markFiscalUnknown(@Param("id") UUID id);

    /** Manual retry re-arm: only FAILED may go back to PENDING (0 rows = reject the retry). */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.PENDING, "
            + "p.fiscalSentAt = :now "
            + "where p.id = :id and p.fiscalStatus = com.yammer.entity.FiscalStatus.FAILED")
    int rearmFailedFiscal(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Operator resolved an UNKNOWN as printed (optionally recording the receipt number). */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.SUCCESS, "
            + "p.receiptNumber = coalesce(:receiptNumber, p.receiptNumber) "
            + "where p.id = :id and p.fiscalStatus = com.yammer.entity.FiscalStatus.UNKNOWN")
    int resolveUnknownAsSuccess(@Param("id") UUID id, @Param("receiptNumber") String receiptNumber);

    /**
     * Operator resolved an UNKNOWN as not printed — re-enables the retry button AND authorizes
     * the bridge to clear its print-intent for this id (else every retry bounces back UNKNOWN).
     */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalStatus = com.yammer.entity.FiscalStatus.FAILED, "
            + "p.fiscalReprintAuthorized = true "
            + "where p.id = :id and p.fiscalStatus = com.yammer.entity.FiscalStatus.UNKNOWN")
    int resolveUnknownAsFailed(@Param("id") UUID id);

    /** Pin the payment to the bridge device that first handled it (first writer wins). */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentEntity p set p.fiscalDevice = :device "
            + "where p.id = :id and p.fiscalDevice is null")
    int pinFiscalDevice(@Param("id") UUID id, @Param("device") String device);
}
