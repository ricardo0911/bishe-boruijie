package com.flowershop.service;

import com.flowershop.dto.CreateSupportTicketRequest;
import com.flowershop.dto.ProcessSupportTicketRequest;
import com.flowershop.dto.SupportTicketResponse;
import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SupportTicketService {

    private static final Set<String> ISSUE_TYPES = Set.of("ORDER", "DELIVERY", "REFUND", "PRODUCT", "ACCOUNT", "OTHER");
    private static final Set<String> PROCESS_STATUSES = Set.of("PROCESSING", "RESOLVED", "CLOSED");
    private static final DateTimeFormatter TICKET_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;

    public SupportTicketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS support_ticket (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                ticket_no VARCHAR(32) NOT NULL,
                user_id BIGINT NOT NULL,
                order_no VARCHAR(64) DEFAULT NULL,
                issue_type VARCHAR(32) NOT NULL,
                title VARCHAR(120) NOT NULL,
                content TEXT NOT NULL,
                contact_name VARCHAR(64) NOT NULL,
                contact_phone VARCHAR(32) NOT NULL,
                status VARCHAR(32) NOT NULL,
                handle_note TEXT DEFAULT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                processed_at DATETIME DEFAULT NULL,
                UNIQUE KEY uk_support_ticket_no (ticket_no),
                KEY idx_support_ticket_status (status),
                KEY idx_support_ticket_user (user_id),
                KEY idx_support_ticket_order_no (order_no),
                KEY idx_support_ticket_created_at (created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客服工单表'
            """);
    }

    public SupportTicketResponse createTicket(CreateSupportTicketRequest request) {
        Long userId = requireUserId(request.getUserId());
        String orderNo = trimToNull(request.getOrderNo());
        String issueType = normalizeIssueType(request.getIssueType());
        String title = requireText(request.getTitle(), "SUPPORT_TICKET_TITLE_REQUIRED", "工单标题不能为空");
        String content = requireText(request.getContent(), "SUPPORT_TICKET_CONTENT_REQUIRED", "工单描述不能为空");
        String contactName = requireText(request.getContactName(), "SUPPORT_TICKET_CONTACT_REQUIRED", "联系人不能为空");
        String contactPhone = requireText(request.getContactPhone(), "SUPPORT_TICKET_PHONE_REQUIRED", "联系电话不能为空");
        String ticketNo = generateTicketNo();
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.update(
            """
            INSERT INTO support_ticket (
                ticket_no, user_id, order_no, issue_type, title, content,
                contact_name, contact_phone, status, handle_note, created_at, updated_at, processed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?)
            """,
            ticketNo,
            userId,
            orderNo,
            issueType,
            title,
            content,
            contactName,
            contactPhone,
            "PENDING",
            null,
            null
        );

        return new SupportTicketResponse(
            null,
            ticketNo,
            userId,
            orderNo,
            issueType,
            title,
            content,
            contactName,
            contactPhone,
            "PENDING",
            null,
            now,
            now,
            null
        );
    }

    public List<SupportTicketResponse> listTickets(String status, String keyword, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizeKnownStatus(normalizedStatus);
        }
        String normalizedKeyword = trimToNull(keyword);

        StringBuilder sql = new StringBuilder("""
            SELECT id, ticket_no, user_id, order_no, issue_type, title, content,
                   contact_name, contact_phone, status, handle_note,
                   created_at, updated_at, processed_at
            FROM support_ticket
            WHERE 1 = 1
            """);
        List<Object> params = new ArrayList<>();

        if (normalizedStatus != null) {
            sql.append(" AND status = ?");
            params.add(normalizedStatus);
        }
        if (normalizedKeyword != null) {
            sql.append(" AND (ticket_no LIKE ? OR order_no LIKE ? OR title LIKE ? OR content LIKE ? OR contact_name LIKE ? OR contact_phone LIKE ?)");
            String like = "%" + normalizedKeyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), supportTicketRowMapper(), params.toArray());
    }

    public List<SupportTicketResponse> listUserTickets(Long userId, String status, Integer limit) {
        Long normalizedUserId = requireUserId(userId);
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizeKnownStatus(normalizedStatus);
        }

        StringBuilder sql = new StringBuilder("""
            SELECT id, ticket_no, user_id, order_no, issue_type, title, content,
                   contact_name, contact_phone, status, handle_note,
                   created_at, updated_at, processed_at
            FROM support_ticket
            WHERE user_id = ?
            """);
        List<Object> params = new ArrayList<>();
        params.add(normalizedUserId);

        if (normalizedStatus != null) {
            sql.append(" AND status = ?");
            params.add(normalizedStatus);
        }

        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit);

        return jdbcTemplate.query(sql.toString(), supportTicketRowMapper(), params.toArray());
    }

    public SupportTicketResponse getTicket(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("SUPPORT_TICKET_NOT_FOUND", "工单不存在");
        }
        List<SupportTicketResponse> results = jdbcTemplate.query(
            """
            SELECT id, ticket_no, user_id, order_no, issue_type, title, content,
                   contact_name, contact_phone, status, handle_note,
                   created_at, updated_at, processed_at
            FROM support_ticket
            WHERE id = ?
            LIMIT 1
            """,
            supportTicketRowMapper(),
            id
        );
        if (results.isEmpty()) {
            throw new BusinessException("SUPPORT_TICKET_NOT_FOUND", "工单不存在");
        }
        return results.get(0);
    }

    @Transactional
    public SupportTicketResponse processTicket(Long id, ProcessSupportTicketRequest request) {
        String status = normalizeProcessStatus(request.getStatus());
        String handleNote = trimToNull(request.getHandleNote());
        SupportTicketResponse existing = getTicket(id);
        LocalDateTime now = LocalDateTime.now();
        Timestamp processedAt = Timestamp.valueOf(now);

        jdbcTemplate.update(
            """
            UPDATE support_ticket
            SET status = ?, handle_note = ?, processed_at = ?, updated_at = NOW()
            WHERE id = ?
            """,
            status,
            handleNote,
            processedAt,
            id
        );

        return new SupportTicketResponse(
            existing.getId(),
            existing.getTicketNo(),
            existing.getUserId(),
            existing.getOrderNo(),
            existing.getIssueType(),
            existing.getTitle(),
            existing.getContent(),
            existing.getContactName(),
            existing.getContactPhone(),
            status,
            handleNote,
            existing.getCreatedAt(),
            now,
            now
        );
    }

    private RowMapper<SupportTicketResponse> supportTicketRowMapper() {
        return (rs, rowNum) -> new SupportTicketResponse(
            rs.getLong("id"),
            rs.getString("ticket_no"),
            rs.getLong("user_id"),
            rs.getString("order_no"),
            rs.getString("issue_type"),
            rs.getString("title"),
            rs.getString("content"),
            rs.getString("contact_name"),
            rs.getString("contact_phone"),
            rs.getString("status"),
            rs.getString("handle_note"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at")),
            toLocalDateTime(rs.getTimestamp("processed_at"))
        );
    }

    private Long requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("SUPPORT_TICKET_USER_INVALID", "用户信息无效，请重新登录后提交");
        }
        return userId;
    }

    private String requireText(String value, String code, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(code, message);
        }
        return normalized;
    }

    private String normalizeIssueType(String issueType) {
        String normalized = requireText(issueType, "SUPPORT_TICKET_TYPE_REQUIRED", "问题类型不能为空").toUpperCase();
        if (!ISSUE_TYPES.contains(normalized)) {
            throw new BusinessException("SUPPORT_TICKET_TYPE_INVALID", "不支持的问题类型");
        }
        return normalized;
    }

    private String normalizeProcessStatus(String status) {
        String normalized = requireText(status, "SUPPORT_TICKET_STATUS_REQUIRED", "处理状态不能为空").toUpperCase();
        if (!PROCESS_STATUSES.contains(normalized)) {
            throw new BusinessException("SUPPORT_TICKET_STATUS_INVALID", "不支持的工单状态");
        }
        return normalized;
    }

    private String normalizeKnownStatus(String status) {
        String normalized = status.toUpperCase();
        if (!"PENDING".equals(normalized) && !PROCESS_STATUSES.contains(normalized)) {
            throw new BusinessException("SUPPORT_TICKET_STATUS_INVALID", "不支持的工单状态");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String generateTicketNo() {
        return "TK" + LocalDateTime.now().format(TICKET_TIME_FORMAT) + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}