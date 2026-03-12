package com.flowershop.service;

import com.flowershop.dto.CreateSupportTicketRequest;
import com.flowershop.dto.ProcessSupportTicketRequest;
import com.flowershop.dto.SupportTicketResponse;
import com.flowershop.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupportTicketServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void createTicketNormalizesFieldsAndDefaultsStatus() {
        SupportTicketService supportTicketService = new SupportTicketService(jdbcTemplate);
        CreateSupportTicketRequest request = new CreateSupportTicketRequest();
        request.setUserId(12L);
        request.setIssueType("order");
        request.setTitle("  配送问题  ");
        request.setContent("  订单已经超时，还没有配送  ");
        request.setOrderNo("  ORD20260309001  ");
        request.setContactName("  小王  ");
        request.setContactPhone(" 13800138000 ");

        when(jdbcTemplate.update(contains("INSERT INTO support_ticket"), ArgumentMatchers.<Object[]>any()))
            .thenReturn(1);

        SupportTicketResponse response = supportTicketService.createTicket(request);

        assertTrue(response.getTicketNo().startsWith("TK"));
        assertEquals("ORDER", response.getIssueType());
        assertEquals("配送问题", response.getTitle());
        assertEquals("订单已经超时，还没有配送", response.getContent());
        assertEquals("ORD20260309001", response.getOrderNo());
        assertEquals("小王", response.getContactName());
        assertEquals("13800138000", response.getContactPhone());
        assertEquals("PENDING", response.getStatus());
        assertNotNull(response.getCreatedAt());

        verify(jdbcTemplate).update(
            contains("INSERT INTO support_ticket"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
        );
    }

    @Test
    void listUserTicketsFiltersByUserIdAndStatus() {
        SupportTicketService supportTicketService = new SupportTicketService(jdbcTemplate);
        SupportTicketResponse existing = new SupportTicketResponse(
            7L,
            "TK202603090001",
            12L,
            "ORD20260309001",
            "ORDER",
            "配送问题",
            "订单已经超时，还没有配送",
            "小王",
            "13800138000",
            "PROCESSING",
            "正在与门店确认配送进度",
            LocalDateTime.of(2026, 3, 9, 10, 0),
            LocalDateTime.of(2026, 3, 9, 11, 0),
            LocalDateTime.of(2026, 3, 9, 11, 0)
        );

        when(jdbcTemplate.query(
            anyString(),
            ArgumentMatchers.<RowMapper<SupportTicketResponse>>any(),
            ArgumentMatchers.eq(12L),
            ArgumentMatchers.eq("PROCESSING"),
            ArgumentMatchers.eq(20)
        )).thenReturn(List.of(existing));

        List<SupportTicketResponse> results = supportTicketService.listUserTickets(12L, "processing", 20);

        assertEquals(1, results.size());
        assertEquals("TK202603090001", results.get(0).getTicketNo());
        assertEquals("PROCESSING", results.get(0).getStatus());

        verify(jdbcTemplate).query(
            contains("FROM support_ticket"),
            ArgumentMatchers.<RowMapper<SupportTicketResponse>>any(),
            ArgumentMatchers.eq(12L),
            ArgumentMatchers.eq("PROCESSING"),
            ArgumentMatchers.eq(20)
        );
    }

    @Test
    void processTicketUpdatesStatusAndHandleNote() {
        SupportTicketService supportTicketService = new SupportTicketService(jdbcTemplate);
        ProcessSupportTicketRequest request = new ProcessSupportTicketRequest();
        request.setStatus("resolved");
        request.setHandleNote("已电话联系用户并补发");

        SupportTicketResponse existing = new SupportTicketResponse(
            7L,
            "TK202603090001",
            12L,
            "ORD20260309001",
            "ORDER",
            "配送问题",
            "订单已经超时，还没有配送",
            "小王",
            "13800138000",
            "PENDING",
            null,
            LocalDateTime.of(2026, 3, 9, 10, 0),
            LocalDateTime.of(2026, 3, 9, 10, 0),
            null
        );

        when(jdbcTemplate.query(
            anyString(),
            ArgumentMatchers.<RowMapper<SupportTicketResponse>>any(),
            ArgumentMatchers.eq(7L)
        )).thenReturn(List.of(existing));
        when(jdbcTemplate.update(contains("UPDATE support_ticket"), ArgumentMatchers.<Object[]>any()))
            .thenReturn(1);

        SupportTicketResponse response = supportTicketService.processTicket(7L, request);

        assertEquals("RESOLVED", response.getStatus());
        assertEquals("已电话联系用户并补发", response.getHandleNote());
        assertNotNull(response.getProcessedAt());

        verify(jdbcTemplate).update(
            contains("UPDATE support_ticket"),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(),
            ArgumentMatchers.any()
        );
    }

    @Test
    void processTicketRejectsUnsupportedStatus() {
        SupportTicketService supportTicketService = new SupportTicketService(jdbcTemplate);
        ProcessSupportTicketRequest request = new ProcessSupportTicketRequest();
        request.setStatus("unknown");
        request.setHandleNote("test");

        BusinessException exception = assertThrows(BusinessException.class, () -> supportTicketService.processTicket(5L, request));

        assertEquals("SUPPORT_TICKET_STATUS_INVALID", exception.getCode());
    }
}