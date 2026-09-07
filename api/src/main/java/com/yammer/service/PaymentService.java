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
                        p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()))
                .toList();
    }
}
