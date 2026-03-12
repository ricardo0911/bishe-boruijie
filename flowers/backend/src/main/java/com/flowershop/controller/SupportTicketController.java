package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.dto.CreateSupportTicketRequest;
import com.flowershop.dto.ProcessSupportTicketRequest;
import com.flowershop.dto.SupportTicketResponse;
import com.flowershop.rbac.RequireRole;
import com.flowershop.rbac.Role;
import com.flowershop.service.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/support-tickets")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @PostMapping
    public ApiResponse<SupportTicketResponse> createTicket(@Valid @RequestBody CreateSupportTicketRequest request) {
        return ApiResponse.success("工单提交成功", supportTicketService.createTicket(request));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<SupportTicketResponse>> listUserTickets(
        @PathVariable("userId") Long userId,
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "limit", defaultValue = "20") Integer limit
    ) {
        return ApiResponse.success(supportTicketService.listUserTickets(userId, status, limit));
    }

    @RequireRole(Role.SUPER_ADMIN)
    @GetMapping
    public ApiResponse<List<SupportTicketResponse>> listTickets(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "keyword", required = false) String keyword,
        @RequestParam(name = "limit", defaultValue = "50") Integer limit
    ) {
        return ApiResponse.success(supportTicketService.listTickets(status, keyword, limit));
    }

    @RequireRole(Role.SUPER_ADMIN)
    @GetMapping("/{id}")
    public ApiResponse<SupportTicketResponse> getTicket(@PathVariable("id") Long id) {
        return ApiResponse.success(supportTicketService.getTicket(id));
    }

    @RequireRole(Role.SUPER_ADMIN)
    @PostMapping("/{id}/process")
    public ApiResponse<SupportTicketResponse> processTicket(
        @PathVariable("id") Long id,
        @Valid @RequestBody ProcessSupportTicketRequest request
    ) {
        return ApiResponse.success("工单处理已更新", supportTicketService.processTicket(id, request));
    }
}