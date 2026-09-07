package com.yammer.controller;

import com.yammer.dto.OrderPointTypeRequest;
import com.yammer.dto.OrderPointTypeResponse;
import com.yammer.service.OrderPointTypeService;
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
@RequestMapping("/order-point-types")
@RequiredArgsConstructor
public class OrderPointTypeController {

    private final OrderPointTypeService orderPointTypeService;

    @GetMapping
    public List<OrderPointTypeResponse> list() {
        return orderPointTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public OrderPointTypeResponse create(@Valid @RequestBody OrderPointTypeRequest request) {
        return orderPointTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public OrderPointTypeResponse update(@PathVariable UUID id, @Valid @RequestBody OrderPointTypeRequest request) {
        return orderPointTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        orderPointTypeService.delete(id);
    }
}
