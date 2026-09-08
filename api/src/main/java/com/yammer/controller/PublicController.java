package com.yammer.controller;

import com.yammer.dto.CustomerBillResponse;
import com.yammer.dto.CustomerJoinResponse;
import com.yammer.dto.CustomerOrderPointResponse;
import com.yammer.dto.CustomerOrderRequest;
import com.yammer.dto.CustomerOrderResponse;
import com.yammer.dto.MenuItemNode;
import com.yammer.dto.OnlinePaymentStatusResponse;
import com.yammer.dto.netopia.NetopiaIpnRequest;
import com.yammer.dto.netopia.NetopiaIpnResponse;
import com.yammer.entity.CustomerSessionEntity;
import com.yammer.entity.LocationEntity;
import com.yammer.entity.OrderEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.repository.LocationRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.SelfPayTypeRepository;
import com.yammer.repository.TableSessionRepository;
import com.yammer.service.CustomerAccessService;
import com.yammer.service.MenuService;
import com.yammer.service.OnlinePaymentService;
import com.yammer.service.OrderService;
import com.yammer.service.StorageService;
import com.yammer.service.StorageService.StoredObject;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unauthenticated endpoints (permitted via {@code /public/**} in SecurityConfig).
 * The old project's customer-facing surface returns here as it is ported: menu-item
 * images, and the order-point lookup behind the printed customer QR codes.
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final StorageService storageService;
    private final OrderPointRepository orderPointRepository;
    private final TableSessionRepository sessionRepository;
    private final LocationRepository locationRepository;
    private final MenuService menuService;
    private final OrderService orderService;
    private final CustomerAccessService customerAccessService;
    private final OnlinePaymentService onlinePaymentService;
    private final SelfPayTypeRepository selfPayTypeRepository;

    /**
     * The order point a customer landed on by scanning its QR: table + client + menu +
     * open state + self-order mode + the caller's approval status (from its stored token).
     */
    @GetMapping("/order-points/{opId}")
    public CustomerOrderPointResponse orderPoint(
            @PathVariable UUID opId, @RequestParam(required = false) UUID token) {
        OrderPointEntity op = orderPointRepository.findById(opId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        boolean sessionOpen = sessionRepository.findByOrderPointIdAndClosedAtIsNull(op.getId()).isPresent();
        UUID clientId = locationRepository.findById(op.getLocationId())
                .map(LocationEntity::getClientId)
                .orElse(null);
        List<MenuItemNode> menu = op.getMenuId() == null
                ? List.of()
                : menuService.getTreeUnchecked(op.getMenuId());
        return new CustomerOrderPointResponse(
                op.getId(), op.getName(), clientId, sessionOpen, op.getSelfOrderMode(),
                customerAccessService.statusAt(opId, token), selfPaysOnline(op), menu);
    }

    /**
     * Join (or resume) the table's open session. A stored token for the current session
     * resumes as-is; otherwise a PENDING request lands on the assigned waiter's
     * Approvals page. 409 while the table is not open.
     */
    @PostMapping("/order-points/{opId}/join")
    public CustomerJoinResponse join(@PathVariable UUID opId, @RequestBody(required = false) JoinRequest body) {
        CustomerSessionEntity cs = customerAccessService.join(opId, body == null ? null : body.token());
        return new CustomerJoinResponse(cs.getToken(), cs.getStatus());
    }

    public record JoinRequest(UUID token) {
    }

    /**
     * The table's current bill (unpaid + paid lines) for an APPROVED customer device —
     * the token is the proof of access (403 otherwise; 409 while the table is not open).
     */
    @GetMapping("/order-points/{opId}/bill")
    public CustomerBillResponse bill(@PathVariable UUID opId, @RequestParam UUID token) {
        customerAccessService.requireApproved(opId, token);
        return CustomerBillResponse.from(orderService.billUnchecked(opId));
    }

    /**
     * Place a self-service customer order. Only allowed while the table's session is
     * OPEN (409) with this device APPROVED (403). At an ONLINE self-pay point the cart
     * is parked and a Netopia {@code paymentUrl} is returned instead — the order is
     * created only once the gateway IPN confirms payment.
     */
    @PostMapping("/order-points/{opId}/orders")
    public CustomerOrderResponse placeOrder(
            @PathVariable UUID opId, @Valid @RequestBody CustomerOrderRequest request) {
        OrderPointEntity op = orderPointRepository.findById(opId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order point not found"));
        if (selfPaysOnline(op)) {
            return onlinePaymentService.start(op, request);
        }
        OrderEntity order = orderService.placeCustomerOrder(opId, request);
        return CustomerOrderResponse.placed(order.getId(), OrderService.DRAFT.equals(order.getStatus()));
    }

    /**
     * Netopia IPN (server-to-server) — the authoritative payment-status callback. The
     * order is created here on a confirmed payment, never on the browser return.
     */
    @PostMapping("/payments/netopia/notify")
    public NetopiaIpnResponse netopiaNotify(@RequestBody NetopiaIpnRequest request) {
        return onlinePaymentService.handleIpn(request);
    }

    /** Status of an online payment intent — polled by the customer return page. */
    @GetMapping("/payments/{reference}/status")
    public OnlinePaymentStatusResponse paymentStatus(@PathVariable UUID reference) {
        return onlinePaymentService.status(reference);
    }

    private boolean selfPaysOnline(OrderPointEntity op) {
        return op.getSelfPayTypeId() != null && selfPayTypeRepository.findById(op.getSelfPayTypeId())
                .map(t -> "ONLINE".equalsIgnoreCase(t.getType()))
                .orElse(false);
    }

    /**
     * softPOS server-to-server result callback for a PENDING card payment: success
     * closes it, failure releases its order lines back to unpaid. Idempotent.
     */
    @PostMapping("/softpos/callback")
    public void softPosCallback(@RequestBody SoftPosCallback body) {
        orderService.softPosCallback(body.paymentId(), body.success());
    }

    public record SoftPosCallback(UUID paymentId, boolean success) {
    }

    /** Serves a menu-item image by its object key (restricted to the menu-items namespace). */
    @GetMapping("/menu-image")
    public ResponseEntity<byte[]> menuImage(@RequestParam String object) {
        if (object == null || !object.startsWith(MenuService.IMAGE_PREFIX + "/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        StoredObject stored = storageService.get(object)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .body(stored.data());
    }
}
