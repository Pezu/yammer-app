package com.yammer.service;

import com.yammer.dto.NotPaidReportRow;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports → Not paid: the consumption closed without money in a period — every settlement taken with
 * the PROTOCOL or PO payment types, newest first, each with the product lines it covered. The
 * amount is what those lines were worth; nothing was collected for them.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotPaidReportService {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    /** The payment types that close consumption without collecting money. */
    static boolean isNotPaid(String type) {
        return "PROTOCOL".equalsIgnoreCase(type) || "PO".equalsIgnoreCase(type);
    }

    public List<NotPaidReportRow> report(UUID locationId, LocalDate from, LocalDate to) {
        accessGuard.requireAccessibleLocation(locationId);
        if (to.isBefore(from)) {
            LocalDate t = to;
            to = from;
            from = t;
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        Map<UUID, String> typeName = paymentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        Set<UUID> protocolTypes = typeName.entrySet().stream()
                .filter(e -> isNotPaid(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (protocolTypes.isEmpty()) {
            return List.of();
        }
        List<PaymentEntity> payments = paymentRepository.findByLocationIdAndCreatedAtBetween(locationId, start, end)
                .stream()
                .filter(p -> protocolTypes.contains(p.getPaymentTypeId()) && !"FAILED".equals(p.getStatus()))
                .sorted(Comparator.comparing(PaymentEntity::getCreatedAt).reversed())
                .toList();
        if (payments.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<OrderItemEntity>> itemsByPayment = orderItemRepository
                .findByPaymentIdIn(payments.stream().map(PaymentEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(OrderItemEntity::getPaymentId));
        Map<UUID, String> pointName = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        Map<String, String> waiterName = userRepository.findByUsernameIn(payments.stream()
                        .map(PaymentEntity::getCreatedBy).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(UserEntity::getUsername,
                        u -> u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName(),
                        (a, b) -> a));

        return payments.stream().map(p -> new NotPaidReportRow(
                p.getId(),
                pointName.getOrDefault(p.getOrderPointId(), "?"),
                p.getCreatedBy() == null ? "—" : waiterName.getOrDefault(p.getCreatedBy(), p.getCreatedBy()),
                p.getCreatedAt() == null ? null : p.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                typeName.getOrDefault(p.getPaymentTypeId(), "PROTOCOL"),
                p.getAmount() == null ? BigDecimal.ZERO : p.getAmount(),
                itemsByPayment.getOrDefault(p.getId(), List.of()).stream()
                        .sorted(Comparator.comparing((OrderItemEntity i) -> BridgeService.plainName(i.getName()),
                                String.CASE_INSENSITIVE_ORDER))
                        .map(i -> {
                            int qty = i.getQuantity() == null ? 0 : i.getQuantity();
                            BigDecimal price = i.getPrice() == null ? BigDecimal.ZERO : i.getPrice();
                            return new NotPaidReportRow.Line(BridgeService.plainName(i.getName()), qty, price,
                                    price.multiply(BigDecimal.valueOf(qty)));
                        })
                        .toList()))
                .toList();
    }
}
