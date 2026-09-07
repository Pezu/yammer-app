package com.yammer.controller;

import com.yammer.dto.SelfPayTypeRequest;
import com.yammer.dto.SelfPayTypeResponse;
import com.yammer.service.SelfPayTypeService;
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
@RequestMapping("/self-pay-types")
@RequiredArgsConstructor
public class SelfPayTypeController {

    private final SelfPayTypeService selfPayTypeService;

    @GetMapping
    public List<SelfPayTypeResponse> list() {
        return selfPayTypeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER')")
    public SelfPayTypeResponse create(@Valid @RequestBody SelfPayTypeRequest request) {
        return selfPayTypeService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER')")
    public SelfPayTypeResponse update(@PathVariable UUID id, @Valid @RequestBody SelfPayTypeRequest request) {
        return selfPayTypeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER')")
    public void delete(@PathVariable UUID id) {
        selfPayTypeService.delete(id);
    }
}
