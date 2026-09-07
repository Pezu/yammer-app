package com.yammer.controller;

import com.yammer.dto.PaymentReportRow;
import com.yammer.service.PaymentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER')") // reporting is admin-only
public class PaymentController {

    private final PaymentService paymentService;

    /** The payments report for one location (newest first). */
    @GetMapping
    public List<PaymentReportRow> report(@RequestParam UUID locationId) {
        return paymentService.report(locationId);
    }
}
