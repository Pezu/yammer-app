package com.yammer.controller;

import com.yammer.dto.OrderFilterOptionsResponse;
import com.yammer.dto.OrderItemsUpdateRequest;
import com.yammer.dto.OrderPageResponse;
import com.yammer.dto.OrderPointBillResponse;
import com.yammer.dto.OrderReportRow;
import com.yammer.dto.OrderResponse;
import com.yammer.dto.OrderStatusRequest;
import com.yammer.dto.PayRequest;
import com.yammer.dto.PlaceOrderRequest;
import com.yammer.service.OrderReportService;
import com.yammer.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Waiter-facing ordering + the admin orders report. Tenant safety comes from AccessGuard. */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class OrderController {

    private final OrderService orderService;
    private final OrderReportService orderReportService;

    @GetMapping
    public List<OrderResponse> list(@RequestParam UUID orderPointId) {
        return orderService.listByOrderPoint(orderPointId);
    }

    /** The table's combined bill (all orders aggregated per product). */
    @GetMapping("/bill")
    public OrderPointBillResponse bill(@RequestParam UUID orderPointId) {
        return orderService.billByOrderPoint(orderPointId);
    }

    /** Settle the point's unpaid bill with one of its accepted payment types; returns the fresh bill. */
    @PostMapping("/pay")
    public OrderPointBillResponse pay(@Valid @RequestBody PayRequest request) {
        return orderService.pay(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return orderService.place(request);
    }

    /** Move an order to a new kanban status (service board / waiter). */
    @PatchMapping("/{id}/status")
    public void updateStatus(@PathVariable UUID id, @Valid @RequestBody OrderStatusRequest request) {
        orderService.updateStatus(id, request.status());
    }

    // --- orders report (ADMIN/SUPER) ---

    /** Server-side paginated order list for one location, newest first, with optional filters. */
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')")
    public OrderPageResponse listPage(
            @RequestParam UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long orderNo,
            @RequestParam(required = false) UUID orderPointId,
            @RequestParam(required = false) String waiter,
            @RequestParam(required = false) String paid) {
        return orderReportService.listPaged(locationId, page, size, orderNo, orderPointId, waiter, paid);
    }

    /** Order-point and waiter option lists for the orders-report filter combos. */
    @GetMapping("/filter-options")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')")
    public OrderFilterOptionsResponse filterOptions(@RequestParam UUID locationId) {
        return orderReportService.filterOptions(locationId);
    }

    /** Update an order's unpaid item quantities (quantity ≤ 0 deletes the item). */
    @PatchMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')")
    public OrderReportRow updateItems(
            @PathVariable UUID id, @Valid @RequestBody OrderItemsUpdateRequest request) {
        return orderReportService.updateItems(id, request.items());
    }

    /** Delete an order entirely (only when nothing is paid). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SUPER')")
    public void delete(@PathVariable UUID id) {
        orderReportService.deleteOrder(id);
    }
}
