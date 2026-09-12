package com.yammer.controller;

import com.yammer.dto.PaymentPageResponse;
import com.yammer.dto.PaymentReportRow;
import com.yammer.service.PaymentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // reporting + fiscal re-issue are admin-only
public class PaymentController {

    private final PaymentService paymentService;

    /** The payments report for one location (newest first). */
    /** One page of the location's payments (newest first) plus totals over all of them. */
    @GetMapping
    public PaymentPageResponse report(
            @RequestParam UUID locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.reportPage(locationId, page, size);
    }

    /** Re-issue a FAILED fiscal receipt (409 unless FAILED). */
    @PostMapping("/{id}/retry-fiscal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryFiscal(@PathVariable UUID id) {
        paymentService.retryFiscal(id);
    }

    /** Operator verdict on an UNKNOWN receipt: printed (+ optional number) or not printed. */
    @PostMapping("/{id}/resolve-unknown")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveUnknown(@PathVariable UUID id, @RequestBody ResolveUnknownRequest body) {
        paymentService.resolveUnknownFiscal(id, body.printed(), body.receiptNumber());
    }

    public record ResolveUnknownRequest(boolean printed, String receiptNumber) {
    }
}
