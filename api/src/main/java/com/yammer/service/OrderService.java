package com.yammer.service;

import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.OrderItemRequest;
import com.yammer.dto.OrderPointBillResponse;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.PayRequest;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.entity.CustomerSessionEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.ProductEntity;
import com.yammer.entity.TableSessionEntity;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.OrderRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.ProductRepository;
import com.yammer.repository.TableSessionRepository;
import com.yammer.security.AccessGuard;
import com.yammer.security.CurrentUserProvider;
import com.yammer.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    /** The statuses shown on the service kanban board (DELIVERED/CANCELED leave it). */
    public static final Set<String> BOARD_STATUSES = Set.of("ORDERED", "READY");
    private static final Set<String> KANBAN_STATUSES = Set.of("ORDERED", "READY", "DELIVERED", "CANCELED");
    /** Orders that never reach the bill: awaiting approval, or canceled. */
    private static final Set<String> NOT_BILLABLE = Set.of("APPROVAL", "CANCELED");

    private static boolean billable(OrderEntity order) {
        return !NOT_BILLABLE.contains(order.getStatus());
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final TableSessionRepository sessionRepository;
    private final LocationRepository locationRepository;
    private final OrderPointRepository orderPointRepository;
    private final MenuItemRepository menuItemRepository;
    private final ProductRepository productRepository;
    private final AccessGuard accessGuard;
    private final CurrentUserProvider currentUser;
    private final CustomerAccessService customerAccessService;
    private final PaymentTypeRepository paymentTypeRepository;

    /**
     * Places an order at the point, snapshotting line names + prices. Always ORDERED for now.
     * Orders belong to the point's OPEN table session — assigning the table opens one.
     */
    public OrderResponse place(PlaceOrderRequest request) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(request.orderPointId());
        TableSessionEntity session = sessionRepository.findByOrderPointIdAndClosedAtIsNull(op.getId())
                .orElseThrow(() -> badRequest("No open session — assign yourself to the table first"));
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + request.orderPointId()));

        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setSessionId(session.getId());
        order.setCreatedBy(me.username());
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("ORDERED");
        OrderEntity savedOrder = orderRepository.save(order);

        List<OrderItemEntity> toSave = new ArrayList<>(request.items().size());
        for (OrderItemRequest i : request.items()) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(savedOrder.getId());
            item.setMenuItemId(i.menuItemId());
            item.setName(i.name().trim());
            item.setPrice(i.price());
            item.setQuantity(i.quantity());
            toSave.add(item);
        }
        List<OrderItemEntity> savedItems = orderItemRepository.saveAll(toSave);
        return OrderResponse.from(savedOrder, savedItems, op.getName());
    }

    /**
     * Places a PUBLIC customer order (QR page, no authentication). Only menu-item ids +
     * quantities come from the client — name/price are resolved from the table's menu
     * (product names fresh from the catalog, as the customer saw them). Requires the
     * table's session to be OPEN (409 otherwise), an APPROVED customer session (403),
     * and a self-order mode other than DISALLOW. Under CONFIRM the order enters the
     * APPROVAL status — invisible on the kanban and the bill until the waiter approves.
     */
    public OrderEntity placeCustomerOrder(UUID orderPointId, CustomerOrderRequest request) {
        OrderPointEntity op = orderPointRepository.findById(orderPointId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId));
        TableSessionEntity session = sessionRepository.findByOrderPointIdAndClosedAtIsNull(op.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Table is not open"));
        if ("DISALLOW".equals(op.getSelfOrderMode())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Self ordering is not allowed at this table");
        }
        CustomerSessionEntity customer = customerAccessService.requireApproved(orderPointId, request.token());
        if (op.getMenuId() == null) {
            throw badRequest("This table has no menu");
        }
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + orderPointId));

        List<ResolvedLine> lines = resolveCustomerOrderLines(op, request.items());

        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setSessionId(session.getId());
        order.setCustomerSessionId(customer.getId());
        order.setCreatedBy("Customer");
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("CONFIRM".equals(op.getSelfOrderMode()) ? "APPROVAL" : "ORDERED");
        OrderEntity savedOrder = orderRepository.save(order);
        orderItemRepository.saveAll(itemsFor(savedOrder.getId(), lines, null));
        return savedOrder;
    }

    /** A validated, price-snapshotted customer order line (resolved from the point's menu). */
    public record ResolvedLine(UUID menuItemId, String name, BigDecimal price, int quantity) {
    }

    /** Ids of the order + payment created when an online payment is confirmed. */
    public record OnlineOrderResult(UUID orderId, UUID paymentId) {
    }

    /**
     * Validates customer cart lines against the point's default menu and snapshots
     * name (fresh from the product catalog) + price. The client only sends menu-item
     * ids + quantities, so prices can't be tampered with.
     */
    public List<ResolvedLine> resolveCustomerOrderLines(
            OrderPointEntity op, List<CustomerOrderRequest.Line> requested) {
        if (op.getMenuId() == null) {
            throw badRequest("This table has no menu");
        }
        Map<UUID, MenuItemEntity> menuItems = menuItemRepository
                .findAllById(requested.stream().map(CustomerOrderRequest.Line::menuItemId).toList())
                .stream()
                .collect(Collectors.toMap(MenuItemEntity::getId, mi -> mi));
        Map<UUID, ProductEntity> products = productRepository
                .findAllById(menuItems.values().stream()
                        .map(MenuItemEntity::getProductId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, p -> p));
        List<ResolvedLine> out = new ArrayList<>(requested.size());
        for (CustomerOrderRequest.Line line : requested) {
            MenuItemEntity mi = menuItems.get(line.menuItemId());
            if (mi == null || !mi.isOrderable() || !op.getMenuId().equals(mi.getMenuId())) {
                throw badRequest("Item not on this table's menu: " + line.menuItemId());
            }
            ProductEntity product = mi.getProductId() == null ? null : products.get(mi.getProductId());
            out.add(new ResolvedLine(
                    mi.getId(), product != null ? product.getName() : mi.getName(),
                    mi.getPrice(), line.quantity()));
        }
        return out;
    }

    /** Sum of price × quantity over resolved lines, scaled to 2 decimals. */
    public BigDecimal totalOf(List<ResolvedLine> lines) {
        return lines.stream()
                .map(l -> (l.price() == null ? BigDecimal.ZERO : l.price())
                        .multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Creates a fully-paid online order — the confirmed-IPN counterpart of
     * {@link #placeCustomerOrder} for ONLINE self-pay points: an ORDERED order with its
     * items, settled by an ONLINE-type SUCCESS payment. Paid orders skip approval.
     */
    public OnlineOrderResult createPaidOnlineOrder(
            OrderPointEntity op, List<ResolvedLine> lines, UUID storedSessionId, UUID customerSessionId) {
        LocationEntity location = locationRepository.findById(op.getLocationId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order point not found: " + op.getId()));
        // Attach to the CURRENT open session; a table closed mid-payment falls back to
        // the session the payment started on (the money is real — never drop the order).
        UUID sessionId = sessionRepository.findByOrderPointIdAndClosedAtIsNull(op.getId())
                .map(TableSessionEntity::getId)
                .orElse(storedSessionId);
        if (sessionId == null) {
            log.warn("Online order for {} confirmed with no table session", op.getName());
        }

        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(op.getId());
        payment.setSessionId(sessionId);
        payment.setAmount(totalOf(lines));
        payment.setTip(BigDecimal.ZERO);
        payment.setPaymentTypeId(paymentTypeRepository.findByTypeIgnoreCase("ONLINE")
                .map(PaymentTypeEntity::getId)
                .orElse(null));
        payment.setStatus("SUCCESS");
        payment.setCreatedBy("Customer");
        payment.setCreatedAt(LocalDateTime.now());
        PaymentEntity savedPayment = paymentRepository.save(payment);

        OrderEntity order = new OrderEntity();
        order.setOrderNo(orderRepository.maxOrderNoForClient(location.getClientId()) + 1);
        order.setOrderPointId(op.getId());
        order.setSessionId(sessionId);
        order.setCustomerSessionId(customerSessionId);
        order.setCreatedBy("Customer");
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("ORDERED");
        OrderEntity savedOrder = orderRepository.save(order);
        orderItemRepository.saveAll(itemsFor(savedOrder.getId(), lines, savedPayment.getId()));
        return new OnlineOrderResult(savedOrder.getId(), savedPayment.getId());
    }

    private List<OrderItemEntity> itemsFor(UUID orderId, List<ResolvedLine> lines, UUID paymentId) {
        List<OrderItemEntity> out = new ArrayList<>(lines.size());
        for (ResolvedLine line : lines) {
            OrderItemEntity item = new OrderItemEntity();
            item.setOrderId(orderId);
            item.setMenuItemId(line.menuItemId());
            item.setName(line.name());
            item.setPrice(line.price());
            item.setQuantity(line.quantity());
            item.setPaymentId(paymentId);
            out.add(item);
        }
        return out;
    }

    /**
     * The table's combined bill for the CURRENT open session: its orders' lines
     * aggregated per product (first-seen order, keyed by menu item + price so a
     * price change starts a new line). No open session → an empty bill.
     */
    @Transactional(readOnly = true)
    public OrderPointBillResponse billByOrderPoint(UUID orderPointId) {
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(orderPointId);
        Optional<TableSessionEntity> session = sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId);
        List<OrderEntity> orders = session
                .map(s -> orderRepository.findBySessionIdOrderByCreatedAtDesc(s.getId()))
                .orElse(List.of())
                .stream()
                .filter(OrderService::billable)
                .toList();
        Map<UUID, LocalDateTime> orderCreatedAt = orders.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getCreatedAt));
        List<OrderItemEntity> items = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                .stream()
                .sorted(Comparator.comparing(i -> orderCreatedAt.get(i.getOrderId())))
                .toList();

        Map<String, OrderPointBillResponse.OrderPointBillLine> lines = new LinkedHashMap<>();
        for (OrderItemEntity item : items) {
            boolean paid = item.getPaymentId() != null;
            String key = (item.getMenuItemId() != null ? item.getMenuItemId().toString() : item.getName())
                    + "|" + item.getPrice() + "|" + paid + "|" + item.getOriginalPrice();
            OrderPointBillResponse.OrderPointBillLine existing = lines.get(key);
            lines.put(key, existing == null
                    ? new OrderPointBillResponse.OrderPointBillLine(
                            item.getMenuItemId(), item.getName(), item.getPrice(), item.getQuantity(), paid,
                            item.getOriginalPrice())
                    : new OrderPointBillResponse.OrderPointBillLine(
                            existing.menuItemId(), existing.name(), existing.price(),
                            existing.quantity() + item.getQuantity(), paid, existing.originalPrice()));
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal unpaidTotal = BigDecimal.ZERO;
        for (OrderPointBillResponse.OrderPointBillLine line : lines.values()) {
            BigDecimal lineTotal = (line.price() == null ? BigDecimal.ZERO : line.price())
                    .multiply(BigDecimal.valueOf(line.quantity()));
            total = total.add(lineTotal);
            if (!line.paid()) {
                unpaidTotal = unpaidTotal.add(lineTotal);
            }
        }
        return new OrderPointBillResponse(
                op.getName(), op.getPaymentTypeIds(), session.isPresent(), op.getSelfOrderMode(),
                List.copyOf(lines.values()), total, unpaidTotal);
    }

    /**
     * A payment against the point's bill with one of its accepted payment types.
     * FULL settles everything unpaid; PARTIAL settles selected product quantities
     * (splitting lines, as in the old project); AMOUNT allocates a fixed sum over
     * the unpaid lines in ordering order — the last unit may end up partially paid
     * (its price is split into a paid part and an unpaid remainder line).
     * Returns the fresh bill.
     */
    public OrderPointBillResponse pay(PayRequest request) {
        UserPrincipal me = currentUser.require();
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(request.orderPointId());
        if (op.getPaymentTypeIds() == null || !op.getPaymentTypeIds().contains(request.paymentTypeId())) {
            throw badRequest("Payment type not accepted at this order point");
        }
        TableSessionEntity session = sessionRepository.findByOrderPointIdAndClosedAtIsNull(op.getId())
                .orElseThrow(() -> badRequest("Nothing to pay"));
        // Unpaid lines of the open session in deterministic allocation order: oldest
        // order first, then line id as a stable tie-break (order_item carries no timestamp).
        List<OrderEntity> orders = orderRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .filter(OrderService::billable)
                .toList();
        Map<UUID, LocalDateTime> createdAt = orders.stream()
                .collect(Collectors.toMap(OrderEntity::getId, OrderEntity::getCreatedAt));
        List<OrderItemEntity> unpaid = orders.isEmpty()
                ? List.of()
                : orderItemRepository
                        .findByOrderIdInAndPaymentIdIsNull(orders.stream().map(OrderEntity::getId).toList())
                        .stream()
                        .sorted(Comparator
                                .<OrderItemEntity, LocalDateTime>comparing(i -> createdAt.get(i.getOrderId()))
                                .thenComparing(i -> i.getId().toString()))
                        .toList();
        if (unpaid.isEmpty()) {
            throw badRequest("Nothing to pay");
        }
        switch (request.mode()) {
            case FULL -> payFull(request, me, session, unpaid);
            case PARTIAL -> payPartial(request, me, session, unpaid);
            case AMOUNT -> payAmount(request, me, session, unpaid);
        }
        return billByOrderPoint(op.getId());
    }

    // --- full ---

    private void payFull(
            PayRequest request, UserPrincipal me, TableSessionEntity session, List<OrderItemEntity> unpaid) {
        BigDecimal amount = unpaid.stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        PaymentEntity payment = createPayment(request, amount, me, session);
        unpaid.forEach(i -> i.setPaymentId(payment.getId()));
        orderItemRepository.saveAll(unpaid);
    }

    // --- partial: selected product quantities, splitting lines as needed ---

    private record Alloc(OrderItemEntity line, int coveredQty) {
    }

    private record ReqEntry(UUID menuItemId, BigDecimal price, int quantity) {
    }

    private void payPartial(
            PayRequest request, UserPrincipal me, TableSessionEntity session, List<OrderItemEntity> unpaid) {
        if (request.items() == null || request.items().isEmpty()) {
            throw badRequest("Partial payment requires at least one item");
        }
        // Merge duplicate (product, unit-price) entries. A priced entry targets only
        // lines with that exact unit price, so a split remainder is paid separately
        // from the product's regular lines; a null price matches any of the product's.
        Map<String, ReqEntry> requested = new LinkedHashMap<>();
        for (PayRequest.PayItem item : request.items()) {
            String key = item.menuItemId() + "|"
                    + (item.price() == null ? "*" : item.price().stripTrailingZeros().toPlainString());
            requested.merge(key, new ReqEntry(item.menuItemId(), item.price(), item.quantity()),
                    (a, b) -> new ReqEntry(a.menuItemId(), a.price(), a.quantity() + b.quantity()));
        }
        // Plan the allocation (no mutation); `taken` guards against double-allocating a line.
        Map<UUID, Integer> taken = new HashMap<>();
        Map<UUID, Alloc> planByLine = new LinkedHashMap<>();
        BigDecimal amount = BigDecimal.ZERO;
        for (ReqEntry entry : requested.values()) {
            int remaining = entry.quantity();
            for (OrderItemEntity line : unpaid) {
                if (remaining <= 0) {
                    break;
                }
                if (!Objects.equals(line.getMenuItemId(), entry.menuItemId())) {
                    continue;
                }
                if (entry.price() != null && entry.price().compareTo(unitPrice(line)) != 0) {
                    continue;
                }
                int available = line.getQuantity() - taken.getOrDefault(line.getId(), 0);
                if (available <= 0) {
                    continue;
                }
                int take = Math.min(remaining, available);
                taken.merge(line.getId(), take, Integer::sum);
                planByLine.merge(line.getId(), new Alloc(line, take),
                        (a, b) -> new Alloc(a.line(), a.coveredQty() + b.coveredQty()));
                amount = amount.add(unitPrice(line).multiply(BigDecimal.valueOf(take)));
                remaining -= take;
            }
            if (remaining > 0) {
                throw badRequest("Requested quantity exceeds the unpaid quantity for a product");
            }
        }
        // Apply: create the Payment, then stamp/split lines. When a line is partially
        // covered the ORIGINAL stays unpaid with its quantity reduced; a NEW line is
        // inserted under the same order for the paid quantity.
        PaymentEntity payment = createPayment(request, amount.setScale(2, RoundingMode.HALF_UP), me, session);
        for (Alloc alloc : planByLine.values()) {
            OrderItemEntity line = alloc.line();
            if (alloc.coveredQty() == line.getQuantity()) {
                line.setPaymentId(payment.getId());
                orderItemRepository.save(line);
            } else {
                orderItemRepository.save(copyLine(line, alloc.coveredQty(), unitPrice(line), payment.getId()));
                line.setQuantity(line.getQuantity() - alloc.coveredQty());
                orderItemRepository.save(line);
            }
        }
    }

    // --- amount: a fixed sum allocated oldest-first; the last unit may be partially paid ---

    private void payAmount(
            PayRequest request, UserPrincipal me, TableSessionEntity session, List<OrderItemEntity> unpaid) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw badRequest("Amount must be positive");
        }
        BigDecimal unpaidTotal = unpaid.stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = request.amount().min(unpaidTotal).setScale(2, RoundingMode.HALF_UP);
        PaymentEntity payment = createPayment(request, remaining, me, session);

        for (OrderItemEntity line : unpaid) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal unit = unitPrice(line);
            BigDecimal lineValue = lineTotal(line);
            if (remaining.compareTo(lineValue) >= 0) {
                line.setPaymentId(payment.getId());
                orderItemRepository.save(line);
                remaining = remaining.subtract(lineValue);
                continue;
            }
            // The money runs out inside this line: cover the full units it still buys…
            int fullUnits = remaining.divideToIntegralValue(unit).intValue(); // unit > 0 here
            if (fullUnits > 0) {
                orderItemRepository.save(copyLine(line, fullUnits, unit, payment.getId()));
                line.setQuantity(line.getQuantity() - fullUnits);
                remaining = remaining.subtract(unit.multiply(BigDecimal.valueOf(fullUnits)));
            }
            // …then split ONE unit's price into a paid part and an unpaid remainder line.
            if (remaining.signum() > 0) {
                orderItemRepository.save(copyLine(line, 1, remaining, payment.getId()));
                OrderItemEntity remainder = copyLine(line, 1, unit.subtract(remaining), null);
                // keep the FIRST original price when re-splitting an already-partial unit
                remainder.setOriginalPrice(line.getOriginalPrice() != null ? line.getOriginalPrice() : unit);
                orderItemRepository.save(remainder);
                line.setQuantity(line.getQuantity() - 1);
                remaining = BigDecimal.ZERO;
            }
            if (line.getQuantity() == 0) {
                orderItemRepository.delete(line); // fully redistributed into the lines above
            } else {
                orderItemRepository.save(line);
            }
            break;
        }
    }

    // --- helpers ---

    /**
     * Creates the Payment with the lifecycle of its type:
     * CASH → SUCCESS + fiscal-print request to the bridge (logged until the bridge is ported);
     * CARD → PENDING until the softPOS callback confirms; PROTOCOL / PO → SUCCESS, no fiscal.
     */
    private PaymentEntity createPayment(
            PayRequest request, BigDecimal amount, UserPrincipal me, TableSessionEntity session) {
        PaymentEntity payment = new PaymentEntity();
        payment.setOrderPointId(request.orderPointId());
        payment.setSessionId(session.getId());
        payment.setAmount(amount);
        payment.setTip(request.tip() == null ? BigDecimal.ZERO : request.tip());
        payment.setPaymentTypeId(request.paymentTypeId());
        payment.setCreatedBy(me.username());
        payment.setCreatedAt(LocalDateTime.now());
        String type = paymentTypeRepository.findById(request.paymentTypeId())
                .map(PaymentTypeEntity::getType)
                .orElse("");
        payment.setStatus("CARD".equals(type) ? "PENDING" : "SUCCESS");
        PaymentEntity saved = paymentRepository.save(payment);
        switch (type) {
            case "CASH" -> log.info(
                    "BRIDGE fiscal-print request: payment={} amount={} tip={} orderPoint={}",
                    saved.getId(), saved.getAmount(), saved.getTip(), saved.getOrderPointId());
            case "CARD" -> log.info(
                    "softPOS charge request: payment={} amount={} — awaiting callback",
                    saved.getId(), saved.getAmount());
            default -> {
                // PROTOCOL / PO close silently — no fiscal printer (as in the old project)
            }
        }
        return saved;
    }

    /**
     * softPOS result callback for a PENDING card payment (idempotent). Success closes the
     * payment; failure releases its order lines back to unpaid and marks it FAILED.
     */
    public void softPosCallback(UUID paymentId, boolean success) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));
        if (!"PENDING".equals(payment.getStatus())) {
            return;
        }
        if (success) {
            payment.setStatus("SUCCESS");
            paymentRepository.save(payment);
            log.info("softPOS confirmed payment {}", paymentId);
        } else {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            List<OrderItemEntity> items = orderItemRepository.findByPaymentId(paymentId);
            items.forEach(i -> i.setPaymentId(null));
            orderItemRepository.saveAll(items);
            log.info("softPOS reported payment {} FAILED — {} line(s) released", paymentId, items.size());
        }
    }

    /** A sibling line under the same order (name/product copied), optionally already paid. */
    private OrderItemEntity copyLine(OrderItemEntity source, int quantity, BigDecimal price, UUID paymentId) {
        OrderItemEntity copy = new OrderItemEntity();
        copy.setOrderId(source.getOrderId());
        copy.setMenuItemId(source.getMenuItemId());
        copy.setName(source.getName());
        copy.setPrice(price);
        copy.setQuantity(quantity);
        copy.setPaymentId(paymentId);
        return copy;
    }

    private BigDecimal unitPrice(OrderItemEntity line) {
        return line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
    }

    private BigDecimal lineTotal(OrderItemEntity line) {
        return unitPrice(line).multiply(BigDecimal.valueOf(line.getQuantity()));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Moves an order to a new kanban status (ORDERED / READY / DELIVERED / CANCELED).
     * Orders awaiting customer-order APPROVAL are not movable here — they enter the
     * flow through the waiter's approval, never the kanban.
     */
    public void updateStatus(UUID orderId, String status) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order not found: " + orderId));
        accessGuard.requireAccessibleOrderPoint(order.getOrderPointId());
        String wanted = status == null ? "" : status.trim().toUpperCase();
        if (!KANBAN_STATUSES.contains(wanted)) {
            throw badRequest("Unknown status: " + status);
        }
        if (!KANBAN_STATUSES.contains(order.getStatus())) {
            throw badRequest("Order is awaiting approval");
        }
        order.setStatus(wanted);
        orderRepository.save(order);
    }

    /** Whether the session still has unsettled lines (the guard against closing it). */
    @Transactional(readOnly = true)
    public boolean sessionHasUnpaid(UUID sessionId) {
        List<UUID> orderIds = orderRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(OrderService::billable)
                .map(OrderEntity::getId)
                .toList();
        return !orderIds.isEmpty() && !orderItemRepository.findByOrderIdInAndPaymentIdIsNull(orderIds).isEmpty();
    }

    /** Orders of the point's current open session (newest first). */
    @Transactional(readOnly = true)
    public List<OrderResponse> listByOrderPoint(UUID orderPointId) {
        OrderPointEntity op = accessGuard.requireAccessibleOrderPoint(orderPointId);
        List<OrderEntity> orders = sessionRepository.findByOrderPointIdAndClosedAtIsNull(orderPointId)
                .map(s -> orderRepository.findBySessionIdOrderByCreatedAtDesc(s.getId()))
                .orElse(List.of());
        Map<UUID, List<OrderItemEntity>> itemsByOrder = orderItemRepository
                .findByOrderIdIn(orders.stream().map(OrderEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
        return orders.stream()
                .map(o -> OrderResponse.from(o, itemsByOrder.getOrDefault(o.getId(), List.of()), op.getName()))
                .toList();
    }
}
