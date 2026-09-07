package com.yammer.controller;

import com.yammer.dto.AssignableOrderPointResponse;
import com.yammer.dto.OrderPointResponse;
import com.yammer.dto.OrderReportRow;
import com.yammer.service.ApprovalService;
import com.yammer.service.OrderPointAssignmentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Waiter-facing table assignment: any signed-in user manages their own assignments. */
@RestController
@RequestMapping("/order-points")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class OrderPointAssignmentController {

    private final OrderPointAssignmentService assignmentService;
    private final ApprovalService approvalService;

    @GetMapping("/assigned")
    public List<OrderPointResponse> myAssigned() {
        return assignmentService.myAssigned();
    }

    @GetMapping("/assignable")
    public List<AssignableOrderPointResponse> assignable(@RequestParam UUID locationId) {
        return assignmentService.assignable(locationId);
    }

    @PostMapping("/{id}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable UUID id) {
        assignmentService.assign(id);
    }

    @DeleteMapping("/{id}/assign")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID id) {
        assignmentService.unassign(id);
    }

    /** Service kanban: undelivered orders routed to the caller's service stations. */
    @GetMapping("/service-board")
    public List<OrderReportRow> serviceBoard() {
        return assignmentService.serviceBoard();
    }

    /** Set the table's self-order mode (ALLOW / CONFIRM / DISALLOW) — assigned users only. */
    @PutMapping("/{id}/self-order-mode")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setSelfOrderMode(@PathVariable UUID id, @RequestBody SelfOrderModeRequest body) {
        approvalService.setSelfOrderMode(id, body.mode());
    }

    public record SelfOrderModeRequest(String mode) {
    }

    /** Close the table's session (only when fully settled) and free the table. */
    @PostMapping("/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable UUID id) {
        assignmentService.closeTable(id);
    }
}
