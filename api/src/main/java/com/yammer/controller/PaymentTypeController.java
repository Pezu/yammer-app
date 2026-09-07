package com.yammer.controller;

import com.yammer.dto.PaymentTypeRequest;
import com.yammer.dto.PaymentTypeResponse;
import com.yammer.service.PaymentTypeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment-types")
@RequiredArgsConstructor
public class PaymentTypeController {

    private final PaymentTypeService paymentTypeService;

    @GetMapping
    public List<PaymentTypeResponse> list() {
        return paymentTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public PaymentTypeResponse create(@Valid @RequestBody PaymentTypeRequest request) {
        return paymentTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public PaymentTypeResponse update(@PathVariable UUID id, @Valid @RequestBody PaymentTypeRequest request) {
        return paymentTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        paymentTypeService.delete(id);
    }
}
