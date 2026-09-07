package com.yammer.controller;

import com.yammer.dto.ApprovalsResponse;
import com.yammer.service.ApprovalService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The waiter's Approvals page: customer joins + customer orders awaiting confirmation. */
@RestController
@RequestMapping("/approvals")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    public ApprovalsResponse list() {
        return approvalService.list();
    }

    /** Approve or deny a customer device's request to join one of my tables. */
    @PostMapping("/customers/{id}")
    public void decideCustomer(@PathVariable UUID id, @RequestBody Decision body) {
        approvalService.decideCustomer(id, body.approve());
    }

    /** Approve (→ ORDERED) or deny (delete) a customer order awaiting confirmation. */
    @PostMapping("/orders/{id}")
    public void decideOrder(@PathVariable UUID id, @RequestBody Decision body) {
        approvalService.decideOrder(id, body.approve());
    }

    public record Decision(boolean approve) {
    }
}
