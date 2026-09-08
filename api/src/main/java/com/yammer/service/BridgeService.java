package com.yammer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yammer.entity.ConnectionType;
import com.yammer.entity.FiscalStatus;
import com.yammer.entity.IntegrationEntity;
import com.yammer.entity.MenuItemEntity;
import com.yammer.entity.OrderItemEntity;
import com.yammer.entity.OrderPointEntity;
import com.yammer.entity.PaymentEntity;
import com.yammer.entity.PaymentTypeEntity;
import com.yammer.entity.ProductEntity;
import com.yammer.entity.VatTypeEntity;
import com.yammer.event.PaymentCommittedEvent;
import com.yammer.repository.IntegrationRepository;
import com.yammer.repository.MenuItemRepository;
import com.yammer.repository.OrderItemRepository;
import com.yammer.repository.OrderPointRepository;
import com.yammer.repository.PaymentRepository;
import com.yammer.repository.PaymentTypeRepository;
import com.yammer.repository.ProductRepository;
import com.yammer.repository.VatTypeRepository;
import com.yammer.util.Strings;
import com.yammer.ws.BridgeWsHandler;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Backend side of the on-prem bridge (ported from old yammer, fiscal receipts only).
 * A fiscal RECEIPT is sent <strong>best-effort, once</strong>: when the payment commits (or
 * is re-issued from the UI), only while the right bridge is live. There is intentionally
 * <strong>no automatic retry</strong> — an auto-resend could overlap a manual re-issue and
 * print the same fiscal receipt twice. The {@code payment} row tracks the outcome
 * (PENDING → SUCCESS / FAILED / UNKNOWN from RECEIPT_RESULT); {@link FiscalResultSweeper}
 * flips a PENDING payment that never got a result to FAILED. The bridge de-dupes by the
 * stable {@code requestId} = payment id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BridgeService {

    private final BridgeWsHandler handler;
    private final ObjectMapper mapper;
    private final PaymentRepository paymentRepository;
    private final PaymentTypeRepository paymentTypeRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPointRepository orderPointRepository;
    private final IntegrationRepository integrationRepository;
    private final MenuItemRepository menuItemRepository;
    private final ProductRepository productRepository;
    private final VatTypeRepository vatTypeRepository;
    private final PlatformTransactionManager transactionManager;

    /** Short load+stamp step in its own tx so the socket send happens with no DB connection held. */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTx() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ─── fiscal dispatch ─────────────────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCommitted(PaymentCommittedEvent event) {
        sendReceipt(event.paymentId());
    }

    private void sendReceipt(UUID paymentId) {
        if (!handler.isConnected()) {
            log.warn("Bridge offline — fiscal receipt for payment {} not sent (re-issue from the UI when online).",
                    paymentId);
            markFailed(paymentId);
            return;
        }
        Dispatch dispatch;
        try {
            dispatch = txTemplate.execute(status -> buildAndStampFrame(paymentId));
        } catch (Exception e) {
            log.error("Failed to build fiscal RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
            markFailed(paymentId);
            return;
        }
        if (dispatch == null) {
            return; // nothing to fiscalize, already SUCCESS, or not configurable — logged inside
        }
        if (dispatch.targetDevice() != null && !handler.isDeviceConnected(dispatch.targetDevice())) {
            log.warn("Bridge device '{}' offline — fiscal RECEIPT for payment {} marked FAILED "
                    + "(re-issue from the UI when the device reconnects).", dispatch.targetDevice(), paymentId);
            markFailed(paymentId);
            return;
        }
        try {
            String sentDevice = dispatch.targetDevice() != null
                    ? (handler.sendTo(dispatch.targetDevice(), dispatch.frame()) ? dispatch.targetDevice() : null)
                    : handler.sendToAnyReturningDevice(dispatch.frame());
            if (sentDevice != null) {
                // first writer wins: every re-dispatch lands on the same bridge (same de-dup store)
                txTemplate.executeWithoutResult(st -> paymentRepository.pinFiscalDevice(paymentId, sentDevice));
                log.info("Sent fiscal RECEIPT for payment {} to device '{}' (best-effort, no auto-retry).",
                        paymentId, sentDevice);
            } else {
                log.warn("Bridge dropped fiscal RECEIPT for payment {} — marking FAILED.", paymentId);
                markFailed(paymentId);
            }
        } catch (Exception e) {
            log.error("Failed to send fiscal RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
            markFailed(paymentId);
        }
    }

    private void markFailed(UUID paymentId) {
        try {
            txTemplate.executeWithoutResult(status -> paymentRepository.markFiscalFailedFromPending(paymentId));
        } catch (Exception e) {
            log.error("Failed to mark payment {} FAILED: {}", paymentId, e.getMessage(), e);
        }
    }

    /** A routable RECEIPT frame; targetDevice = bridge device id for USB registers, null = any bridge. */
    private record Dispatch(String frame, String targetDevice) {
    }

    private Dispatch buildAndStampFrame(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getFiscalStatus() != FiscalStatus.PENDING) {
            return null;
        }
        OrderPointEntity op = orderPointRepository.findById(payment.getOrderPointId()).orElse(null);
        if (op == null) {
            return null;
        }
        IntegrationEntity reg = op.getCashRegisterId() == null ? null
                : integrationRepository.findById(op.getCashRegisterId()).orElse(null);
        if (reg == null) {
            log.warn("No cash register configured for order point '{}' — payment {} marked FAILED.",
                    op.getName(), payment.getId());
            paymentRepository.markFiscalFailedFromPending(paymentId);
            return null;
        }
        boolean viaMobile = reg.getConnection() == ConnectionType.MOBILE;
        // a register attached to a phone prints only through that phone's bridge session
        String pinned = Strings.trimToNull(payment.getFiscalDevice());
        String targetDevice = pinned != null ? pinned : viaMobile ? Strings.trimToNull(reg.getDeviceId()) : null;
        String cashRegisterIp = Strings.trimToNull(reg.getIp());
        if (viaMobile && targetDevice == null) {
            log.warn("Cash register '{}' has no phone attached — payment {} marked FAILED.", reg.getName(), paymentId);
            paymentRepository.markFiscalFailedFromPending(paymentId);
            return null;
        }
        if (!viaMobile && cashRegisterIp == null) {
            log.warn("Cash register '{}' has no IP — payment {} marked FAILED.", reg.getName(), paymentId);
            paymentRepository.markFiscalFailedFromPending(paymentId);
            return null;
        }
        List<OrderItemEntity> items = orderItemRepository.findByPaymentId(paymentId);
        if (items.isEmpty()) {
            log.warn("Payment {} has no lines — nothing to fiscalize.", paymentId);
            paymentRepository.markFiscalFailedFromPending(paymentId);
            return null;
        }
        Map<UUID, BigDecimal> vatByMenuItem = resolveVat(items);

        List<Map<String, Object>> lines = new ArrayList<>(items.size() + 1);
        for (OrderItemEntity it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", plainName(it.getName()));
            m.put("quantity", it.getQuantity());
            m.put("unitPrice", it.getPrice());
            m.put("vat", it.getMenuItemId() == null ? null : vatByMenuItem.get(it.getMenuItemId()));
            lines.add(m);
        }
        BigDecimal tip = payment.getTip();
        if (tip != null && tip.signum() > 0) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", "Tips");
            m.put("quantity", 1);
            m.put("unitPrice", tip);
            m.put("vat", BigDecimal.ZERO);
            lines.add(m);
        }
        String method = payment.getPaymentTypeId() == null ? "CASH"
                : paymentTypeRepository.findById(payment.getPaymentTypeId())
                        .map(PaymentTypeEntity::getType).orElse("CASH");
        if ("ONLINE".equalsIgnoreCase(method)) {
            method = "CARD"; // the register knows cash / card / check
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "RECEIPT");
        msg.put("requestId", payment.getId().toString());
        msg.put("fiscal", true);
        msg.put("paymentMethod", method.toUpperCase());
        msg.put("cashRegister", cashRegisterIp);
        if (payment.isFiscalReprintAuthorized()) {
            msg.put("clearIntent", true); // operator verified the ambiguous attempt did NOT print
        }
        msg.put("lines", lines);
        try {
            String frame = mapper.writeValueAsString(msg);
            payment.setFiscalSentAt(LocalDateTime.now());
            paymentRepository.save(payment);
            return new Dispatch(frame, targetDevice);
        } catch (Exception e) {
            log.error("Failed to serialize RECEIPT for payment {}: {}", paymentId, e.getMessage(), e);
            paymentRepository.markFiscalFailedFromPending(paymentId);
            return null;
        }
    }

    /** Product names are rich text: strip markup (and the small description block) to one line. */
    static String plainName(String html) {
        if (html == null) {
            return "";
        }
        String s = html
                .replaceAll("(?is)<font[^>]*size=[\"']?1[\"']?[^>]*>.*?</font>", "")
                .replaceAll("(?is)<small\\b[^>]*>.*?</small>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&quot;", "\"");
        for (String line : s.split("\n")) {
            String t = line.trim().replaceAll("\\s+", " ");
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    // ─── result write-back ───────────────────────────────────────────────────

    /** Apply a {@code RECEIPT_RESULT}: guarded transitions, SUCCESS terminal, UNKNOWN parks for an operator. */
    @Transactional
    public void onResult(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            String requestId = node.path("requestId").asText(null);
            if (requestId == null) {
                return;
            }
            UUID paymentId;
            try {
                paymentId = UUID.fromString(requestId);
            } catch (IllegalArgumentException notAPayment) {
                return;
            }
            String status = node.path("status").asText("");
            String receiptNumber = Strings.trimToNull(node.path("receiptNumber").asText(null));
            int updated;
            if ("OK".equalsIgnoreCase(status)) {
                updated = paymentRepository.markFiscalSuccess(paymentId, receiptNumber);
            } else if ("UNKNOWN".equalsIgnoreCase(status)) {
                updated = paymentRepository.markFiscalUnknown(paymentId);
            } else {
                updated = paymentRepository.markFiscalFailedFromPending(paymentId);
            }
            if (updated == 0) {
                log.info("Fiscal result '{}' for payment {} ignored (terminal or non-matching status).", status, paymentId);
            } else {
                log.info("Fiscal result for payment {}: {} {}", paymentId, status,
                        node.path("errorCode").isMissingNode() ? "" : node.path("errorCode").asText(""));
            }
        } catch (Exception e) {
            log.error("Failed to apply fiscal result: {}", e.getMessage(), e);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    /** menuItemId → VAT percentage, via the menu item's product (VAT lives on the product). */
    private Map<UUID, BigDecimal> resolveVat(List<OrderItemEntity> items) {
        Set<UUID> menuItemIds = items.stream()
                .map(OrderItemEntity::getMenuItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (menuItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> productByMenuItem = new HashMap<>();
        for (MenuItemEntity mi : menuItemRepository.findAllById(menuItemIds)) {
            if (mi.getProductId() != null) {
                productByMenuItem.put(mi.getId(), mi.getProductId());
            }
        }
        Map<UUID, UUID> vatTypeByProduct = new HashMap<>();
        for (ProductEntity p : productRepository.findAllById(new HashSet<>(productByMenuItem.values()))) {
            if (p.getVatTypeId() != null) {
                vatTypeByProduct.put(p.getId(), p.getVatTypeId());
            }
        }
        Map<UUID, BigDecimal> valueByVatType = vatTypeRepository
                .findAllById(new HashSet<>(vatTypeByProduct.values())).stream()
                .collect(Collectors.toMap(VatTypeEntity::getId, VatTypeEntity::getValue));
        Map<UUID, BigDecimal> result = new HashMap<>();
        productByMenuItem.forEach((menuItemId, productId) -> {
            UUID vatTypeId = vatTypeByProduct.get(productId);
            BigDecimal value = vatTypeId == null ? null : valueByVatType.get(vatTypeId);
            if (value != null) {
                result.put(menuItemId, value);
            }
        });
        return result;
    }
}
