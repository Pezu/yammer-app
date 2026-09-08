package com.yammer.service;

import com.yammer.dto.PaymentReportRow;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import com.yammer.entity.FiscalStatus;
import com.yammer.event.PaymentCommittedEvent;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderPointRepository orderPointRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * The location's payments, newest first, with table/waiter/payment-type names resolved.
     * FAILED (softPOS-declined) payments are excluded — their lines went back to unpaid.
     */
    public List<PaymentReportRow> report(UUID locationId) {
        accessGuard.requireAccessibleLocation(locationId);
        List<PaymentEntity> payments = paymentRepository.findByLocationId(locationId).stream()
                .filter(p -> !"FAILED".equals(p.getStatus()))
                .toList();

        Map<UUID, String> pointNames = orderPointRepository
                .findAllById(payments.stream().map(PaymentEntity::getOrderPointId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        Map<UUID, String> typeNames = paymentTypeRepository
                .findAllById(payments.stream()
                        .map(PaymentEntity::getPaymentTypeId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        Map<String, String> waiterNames = userRepository
                .findByUsernameIn(payments.stream()
                        .map(PaymentEntity::getCreatedBy)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(
                        UserEntity::getUsername,
                        u -> u.getName() != null ? u.getName() : u.getUsername()));

        return payments.stream()
                .map(p -> new PaymentReportRow(
                        p.getId(),
                        pointNames.getOrDefault(p.getOrderPointId(), "?"),
                        p.getCreatedBy() == null
                                ? "—"
                                : waiterNames.getOrDefault(p.getCreatedBy(), p.getCreatedBy()),
                        p.getAmount(),
                        p.getTip(),
                        p.getAmount().add(p.getTip() == null ? BigDecimal.ZERO : p.getTip()),
                        p.getPaymentTypeId() == null
                                ? "—"
                                : typeNames.getOrDefault(p.getPaymentTypeId(), "?"),
                        p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                        p.getFiscalStatus().name(),
                        p.getReceiptNumber()))
                .toList();
    }

    /**
     * Manually re-issue a FAILED fiscal receipt. The re-arm is one guarded UPDATE: a result
     * committing concurrently (FAILED → SUCCESS) makes it match 0 rows instead of clobbering
     * the SUCCESS. The stamped fiscalSentAt restarts the sweeper's deadline from now.
     */
    public void retryFiscal(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        accessGuard.requireAccessibleOrderPoint(payment.getOrderPointId());
        if (payment.getFiscalStatus() == FiscalStatus.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This payment is not fiscalized");
        }
        int rearmed = paymentRepository.rearmFailedFiscal(paymentId, LocalDateTime.now());
        if (rearmed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only failed receipts can be re-issued (current status: " + payment.getFiscalStatus() + ")");
        }
        eventPublisher.publishEvent(new PaymentCommittedEvent(paymentId)); // one send after commit
    }

    /**
     * Operator verdict on an UNKNOWN fiscal receipt after checking the register: printed
     * (optionally with the receipt number) or not printed (re-enables retry + authorizes the
     * bridge to clear its print-intent).
     */
    public void resolveUnknownFiscal(UUID paymentId, boolean printed, String receiptNumber) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        accessGuard.requireAccessibleOrderPoint(payment.getOrderPointId());
        String number = receiptNumber == null || receiptNumber.isBlank() ? null : receiptNumber.trim();
        int updated = printed
                ? paymentRepository.resolveUnknownAsSuccess(paymentId, number)
                : paymentRepository.resolveUnknownAsFailed(paymentId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only UNKNOWN receipts can be resolved (current status: " + payment.getFiscalStatus() + ")");
        }
    }
}
