package com.yammer.service;

import com.yammer.dto.DashboardResponse;
import com.yammer.dto.DashboardResponse.Bucket;
import com.yammer.dto.DashboardResponse.FinalRow;
import com.yammer.dto.DashboardResponse.PaymentTypeRow;
import com.yammer.dto.DashboardResponse.ProductRow;
import com.yammer.dto.DashboardResponse.Summary;
import com.yammer.dto.DashboardResponse.TableRow;
import com.yammer.dto.DashboardResponse.WaiterRow;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.UserEntity;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.UserRepository;
import com.yammer.security.AccessGuard;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The backoffice dashboard: one location, one date range (inclusive days, server local time),
 * aggregated in memory — a venue's day is a few hundred rows. Orders count by their creation
 * time (DRAFT and CANCELED excluded), payments by their own time; tips ride on payments.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardReportService {

    private static final Set<String> NOT_BILLABLE = Set.of("DRAFT", "CANCELED");
    private static final DateTimeFormatter BUCKET_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final OrderPointRepository orderPointRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;

    public DashboardResponse dashboard(UUID locationId, LocalDate from, LocalDate to) {
        accessGuard.requireAccessibleLocation(locationId);
        if (to.isBefore(from)) {
            LocalDate t = to;
            to = from;
            from = t;
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        List<OrderEntity> orders = orderRepository.findByLocationIdAndCreatedAtBetween(locationId, start, end)
                .stream().filter(o -> !NOT_BILLABLE.contains(o.getStatus())).toList();
        List<OrderItemEntity> items = orders.isEmpty() ? List.of()
                : orderItemRepository.findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList());
        List<PaymentEntity> payments = paymentRepository.findByLocationIdAndCreatedAtBetween(locationId, start, end)
                .stream().filter(p -> !"FAILED".equals(p.getStatus())).toList();

        Map<UUID, OrderEntity> orderById = orders.stream().collect(Collectors.toMap(OrderEntity::getId, o -> o));
        items = items.stream().filter(i -> orderById.containsKey(i.getOrderId())).toList(); // billable orders only
        Map<UUID, String> pointName = orderPointRepository.findByLocationIdOrderByName(locationId).stream()
                .collect(Collectors.toMap(OrderPointEntity::getId, OrderPointEntity::getName));
        Map<UUID, String> typeName = paymentTypeRepository.findAll().stream()
                .collect(Collectors.toMap(PaymentTypeEntity::getId, PaymentTypeEntity::getType));
        Map<String, String> displayName = displayNames(orders, payments);

        // ---- summary
        BigDecimal ordered = sum(items.stream().map(this::lineTotal));
        BigDecimal remaining = sum(items.stream().filter(i -> i.getPaymentId() == null).map(this::lineTotal));
        BigDecimal paid = sum(payments.stream().map(PaymentEntity::getAmount));
        BigDecimal tips = sum(payments.stream().map(PaymentEntity::getTip));
        BigDecimal averageOrder = orders.isEmpty() ? BigDecimal.ZERO
                : ordered.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
        Summary summary = new Summary(ordered, paid, tips, remaining, orders.size(), payments.size(), averageOrder);

        // ---- timeline
        int bucketMinutes = bucketMinutesFor(Duration.between(start, end));
        TreeMap<LocalDateTime, BigDecimal[]> buckets = new TreeMap<>(); // [ordered, paid, orders]
        for (LocalDateTime t = start; t.isBefore(end); t = t.plusMinutes(bucketMinutes)) {
            buckets.put(t, new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        }
        Map<UUID, BigDecimal> orderTotal = new HashMap<>();
        items.forEach(i -> orderTotal.merge(i.getOrderId(), lineTotal(i), BigDecimal::add));
        for (OrderEntity o : orders) {
            BigDecimal[] b = buckets.get(bucketOf(o.getCreatedAt(), start, bucketMinutes));
            if (b != null) {
                b[0] = b[0].add(orderTotal.getOrDefault(o.getId(), BigDecimal.ZERO));
                b[2] = b[2].add(BigDecimal.ONE);
            }
        }
        for (PaymentEntity p : payments) {
            BigDecimal[] b = buckets.get(bucketOf(p.getCreatedAt(), start, bucketMinutes));
            if (b != null) {
                b[1] = b[1].add(nz(p.getAmount()));
            }
        }
        List<Bucket> series = buckets.entrySet().stream()
                .map(e -> new Bucket(e.getKey().format(BUCKET_FMT), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2].longValue()))
                .toList();

        // ---- tables
        Map<String, BigDecimal[]> byTable = new TreeMap<>((a, b) -> OrderPointService.compareNames(a, b));
        Function<String, BigDecimal[]> tableAcc = k -> byTable.computeIfAbsent(k, x -> zeros(6));
        for (OrderItemEntity i : items) {
            OrderEntity o = orderById.get(i.getOrderId());
            BigDecimal[] acc = tableAcc.apply(pointName.getOrDefault(o.getOrderPointId(), "?"));
            acc[0] = acc[0].add(lineTotal(i));
            if (i.getPaymentId() == null) {
                acc[5] = acc[5].add(lineTotal(i));
            }
        }
        for (PaymentEntity p : payments) {
            BigDecimal[] acc = tableAcc.apply(pointName.getOrDefault(p.getOrderPointId(), "?"));
            int slot = switch (kind(typeName.get(p.getPaymentTypeId()))) { case CASH -> 1; case CARD -> 2; default -> 3; };
            acc[slot] = acc[slot].add(nz(p.getAmount()));
            acc[4] = acc[4].add(nz(p.getTip()));
        }
        List<TableRow> tables = byTable.entrySet().stream()
                .map(e -> new TableRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2],
                        e.getValue()[3], e.getValue()[4], e.getValue()[5]))
                .toList();

        // ---- products (by plain product name)
        Map<String, BigDecimal[]> byProduct = new LinkedHashMap<>(); // [qty, sales]
        for (OrderItemEntity i : items) {
            BigDecimal[] acc = byProduct.computeIfAbsent(BridgeService.plainName(i.getName()), x -> zeros(2));
            acc[0] = acc[0].add(BigDecimal.valueOf(nzQty(i)));
            acc[1] = acc[1].add(lineTotal(i));
        }
        List<ProductRow> products = byProduct.entrySet().stream()
                .map(e -> new ProductRow(e.getKey(), e.getValue()[0].longValue(), e.getValue()[1]))
                .sorted(Comparator.comparing(ProductRow::quantity).reversed().thenComparing(ProductRow::product))
                .toList();

        // ---- waiters: orders/sales/unsettled by the order's creator; takings/tips by the payment's creator
        Map<String, BigDecimal[]> byWaiter = new TreeMap<>(); // [orders, sales, cash, card, other, tipsCash, tipsCard, unsettled]
        Function<String, BigDecimal[]> waiterAcc = k -> byWaiter.computeIfAbsent(k, x -> zeros(8));
        for (OrderEntity o : orders) {
            waiterAcc.apply(displayName.getOrDefault(o.getCreatedBy(), who(o.getCreatedBy())))[0] =
                    waiterAcc.apply(displayName.getOrDefault(o.getCreatedBy(), who(o.getCreatedBy())))[0].add(BigDecimal.ONE);
        }
        for (OrderItemEntity i : items) {
            OrderEntity o = orderById.get(i.getOrderId());
            BigDecimal[] acc = waiterAcc.apply(displayName.getOrDefault(o.getCreatedBy(), who(o.getCreatedBy())));
            acc[1] = acc[1].add(lineTotal(i));
            if (i.getPaymentId() == null) {
                acc[7] = acc[7].add(lineTotal(i));
            }
        }
        for (PaymentEntity p : payments) {
            BigDecimal[] acc = waiterAcc.apply(displayName.getOrDefault(p.getCreatedBy(), who(p.getCreatedBy())));
            switch (kind(typeName.get(p.getPaymentTypeId()))) {
                case CASH -> { acc[2] = acc[2].add(nz(p.getAmount())); acc[5] = acc[5].add(nz(p.getTip())); }
                case CARD -> { acc[3] = acc[3].add(nz(p.getAmount())); acc[6] = acc[6].add(nz(p.getTip())); }
                default -> acc[4] = acc[4].add(nz(p.getAmount()));
            }
        }
        List<WaiterRow> waiters = byWaiter.entrySet().stream()
                .map(e -> new WaiterRow(e.getKey(), e.getValue()[0].longValue(), e.getValue()[1], e.getValue()[2],
                        e.getValue()[3], e.getValue()[4], e.getValue()[5], e.getValue()[6], e.getValue()[7]))
                .sorted(Comparator.comparing(WaiterRow::sales).reversed())
                .toList();

        // ---- payment types
        Map<String, BigDecimal[]> byType = new TreeMap<>(); // [count, amount, tips]
        for (PaymentEntity p : payments) {
            BigDecimal[] acc = byType.computeIfAbsent(typeName.getOrDefault(p.getPaymentTypeId(), "?"), x -> zeros(3));
            acc[0] = acc[0].add(BigDecimal.ONE);
            acc[1] = acc[1].add(nz(p.getAmount()));
            acc[2] = acc[2].add(nz(p.getTip()));
        }
        List<PaymentTypeRow> paymentTypes = byType.entrySet().stream()
                .map(e -> new PaymentTypeRow(e.getKey(), e.getValue()[0].longValue(), e.getValue()[1], e.getValue()[2]))
                .toList();

        // ---- final report (the old app's per-waiter slip: CASH and CARD only)
        List<FinalRow> finalReport = waiters.stream()
                .filter(w -> w.paidCard().signum() != 0 || w.paidCash().signum() != 0
                        || w.tipsCard().signum() != 0 || w.tipsCash().signum() != 0)
                .map(w -> new FinalRow(w.waiter(), w.paidCard(), w.paidCash(), w.tipsCard(), w.tipsCash(),
                        w.paidCard().add(w.paidCash()).add(w.tipsCard()).add(w.tipsCash())))
                .toList();

        return new DashboardResponse(summary, series, bucketMinutes, tables, products, waiters, paymentTypes, finalReport);
    }

    private enum Kind { CASH, CARD, OTHER }

    private static Kind kind(String type) {
        if (type == null) {
            return Kind.OTHER;
        }
        return switch (type.toUpperCase()) {
            case "CASH" -> Kind.CASH;
            case "CARD", "ONLINE" -> Kind.CARD;
            default -> Kind.OTHER;
        };
    }

    /** 15-minute buckets for a single day, hourly up to a week, daily beyond. */
    private static int bucketMinutesFor(Duration range) {
        if (range.toDays() <= 1) {
            return 15;
        }
        return range.toDays() <= 7 ? 60 : 24 * 60;
    }

    private static LocalDateTime bucketOf(LocalDateTime t, LocalDateTime start, int minutes) {
        long idx = Duration.between(start, t).toMinutes() / minutes;
        return start.plusMinutes(idx * minutes);
    }

    private Map<String, String> displayNames(List<OrderEntity> orders, List<PaymentEntity> payments) {
        Set<String> usernames = new java.util.HashSet<>();
        orders.forEach(o -> { if (o.getCreatedBy() != null) usernames.add(o.getCreatedBy()); });
        payments.forEach(p -> { if (p.getCreatedBy() != null) usernames.add(p.getCreatedBy()); });
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByUsernameIn(usernames).stream()
                .collect(Collectors.toMap(UserEntity::getUsername,
                        u -> u.getName() == null || u.getName().isBlank() ? u.getUsername() : u.getName(),
                        (a, b) -> a));
    }

    private static String who(String username) {
        return username == null || username.isBlank() ? "—" : username;
    }

    private BigDecimal lineTotal(OrderItemEntity i) {
        return nz(i.getPrice()).multiply(BigDecimal.valueOf(nzQty(i)));
    }

    private static int nzQty(OrderItemEntity i) {
        return i.getQuantity() == null ? 0 : i.getQuantity();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal sum(java.util.stream.Stream<BigDecimal> values) {
        return values.filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        java.util.Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    /** The final report rows as the thermal slip expects them (one per waiter). */
    public List<FinalRow> finalReport(UUID locationId, LocalDate from, LocalDate to) {
        return dashboard(locationId, from, to).finalReport();
    }

}
