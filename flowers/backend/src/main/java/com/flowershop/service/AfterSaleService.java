package com.flowershop.service;

import com.flowershop.dto.AuditRefundRequest;
import com.flowershop.dto.ProcessRefundRequest;
import com.flowershop.dto.RefundRequest;
import com.flowershop.dto.RefundResponse;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 售后服务Service
 * 处理退款申请、审核、退款处理流程
 */
@Service
public class AfterSaleService {

    private static final Logger logger = LoggerFactory.getLogger(AfterSaleService.class);

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;

    public AfterSaleService(JdbcTemplate jdbcTemplate, InventoryService inventoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS after_sale_record (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              refund_no VARCHAR(32) NOT NULL,
              order_no VARCHAR(32) NOT NULL,
              order_id BIGINT NOT NULL,
              refund_amount DECIMAL(12, 2) NOT NULL,
              reason VARCHAR(255) NOT NULL,
              description TEXT NULL,
              evidence_images VARCHAR(2000) NULL,
              status VARCHAR(32) NOT NULL,
              reject_reason VARCHAR(500) NULL,
              transaction_id VARCHAR(64) NULL,
              apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              audit_time DATETIME NULL,
              refund_time DATETIME NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              UNIQUE KEY uk_refund_no (refund_no),
              KEY idx_after_sale_order_id (order_id),
              KEY idx_after_sale_order_no (order_no),
              KEY idx_after_sale_status (status),
              KEY idx_after_sale_apply_time (apply_time)
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """
        );

        jdbcTemplate.execute("ALTER TABLE after_sale_record CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        addColumnIfMissing(
            "after_sale_record",
            "original_order_status",
            "ALTER TABLE after_sale_record ADD COLUMN original_order_status VARCHAR(32) NULL COMMENT 'original order status' AFTER evidence_images"
        );
    }

        private void addColumnIfMissing(String tableName, String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            tableName,
            columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    /**
     * 申请退款
     * 订单状态: PAID/CONFIRMED/COMPLETED -> REFUND_REQUESTED
     */
    @Transactional
    public RefundResponse applyRefund(RefundRequest request) {
        // 1. 查询订单信息（带锁）
        OrderSnapshot order = getOrderForUpdate(request.getOrderNo());

        // 2. 验证订单状态是否允许申请退款
        if (!isRefundableStatus(order.status())) {
            logger.warn("订单状态不可申请退款, orderNo: {}, status: {}", request.getOrderNo(), order.status());
            throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可申请退款: " + order.status());
        }

        // 3. 验证退款金额
        if (request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("REFUND_AMOUNT_INVALID", "退款金额必须大于0");
        }
        if (request.getRefundAmount().compareTo(order.paymentAmount()) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEED", "退款金额不能超过支付金额");
        }

        // 4. 检查是否已有进行中的售后申请
        if (hasPendingAfterSale(order.id())) {
            throw new BusinessException("PENDING_AFTER_SALE_EXISTS", "该订单已有进行中的售后申请");
        }

        // 5. 生成退款单号
        String refundNo = generateRefundNo();

        // 6. 保存退款申请记录
        Long afterSaleId = insertAfterSaleRecord(order, refundNo, request);

        // 7. 更新订单状态为退款申请中
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'REFUND_REQUESTED', updated_at = NOW()
            WHERE id = ?
            """,
            order.id()
        );

        logger.info("退款申请提交成功, orderNo: {}, refundNo: {}, amount: {}",
                request.getOrderNo(), refundNo, request.getRefundAmount());

        return getRefundResponse(afterSaleId);
    }

    /**
     * 查询售后记录
     */
    public RefundResponse getAfterSaleByOrderNo(String orderNo) {
        OrderSnapshot order = getOrderByNo(orderNo);

        List<RefundResponse> records = jdbcTemplate.query(
            """
            SELECT id, refund_no, order_no, order_id, refund_amount, reason, description,
                   evidence_images, status, reject_reason, transaction_id,
                   apply_time, audit_time, refund_time, created_at, updated_at
            FROM after_sale_record
            WHERE order_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (rs, rowNum) -> new RefundResponse(
                rs.getLong("id"),
                rs.getString("refund_no"),
                rs.getString("order_no"),
                rs.getLong("order_id"),
                rs.getBigDecimal("refund_amount"),
                rs.getString("reason"),
                rs.getString("description"),
                rs.getString("evidence_images"),
                rs.getString("status"),
                rs.getString("reject_reason"),
                rs.getString("transaction_id"),
                toLocalDateTime(rs.getTimestamp("apply_time")),
                toLocalDateTime(rs.getTimestamp("audit_time")),
                toLocalDateTime(rs.getTimestamp("refund_time")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
            ),
            order.id()
        );

        if (records.isEmpty()) {
            return null;
        }
        return records.get(0);
    }

    /**
     * 审核通过
     * 状态: REFUND_REQUESTED -> REFUNDING
     */
    @Transactional
    public RefundResponse approveRefund(Long afterSaleId) {
        // 1. 查询售后记录（带锁）
        AfterSaleRecord record = getAfterSaleForUpdate(afterSaleId);

        // 2. 验证状态
        if (!"REFUND_REQUESTED".equals(record.status())) {
            throw new BusinessException("AFTER_SALE_STATUS_INVALID", "当前状态不可审核通过: " + record.status());
        }

        // 3. 更新售后记录状态
        jdbcTemplate.update(
            """
            UPDATE after_sale_record
            SET status = 'REFUNDING', audit_time = NOW(), updated_at = NOW()
            WHERE id = ?
            """,
            afterSaleId
        );

        // 4. 更新订单状态
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'REFUNDING', updated_at = NOW()
            WHERE id = ?
            """,
            record.orderId()
        );

        logger.info("退款审核通过, afterSaleId: {}, refundNo: {}", afterSaleId, record.refundNo());

        return getRefundResponse(afterSaleId);
    }

    /**
     * 审核拒绝
     * 状态: REFUND_REQUESTED -> 回到原订单状态 (PAID/CONFIRMED)
     */
    @Transactional
    public RefundResponse rejectRefund(Long afterSaleId, AuditRefundRequest request) {
        // 1. 查询售后记录（带锁）
        AfterSaleRecord record = getAfterSaleForUpdate(afterSaleId);

        // 2. 验证状态
        if (!"REFUND_REQUESTED".equals(record.status())) {
            throw new BusinessException("AFTER_SALE_STATUS_INVALID", "当前状态不可审核拒绝: " + record.status());
        }

        // 3. 确定订单应该回到的状态
        String originalStatus = resolveOriginalStatus(record);

        // 4. 更新售后记录状态
        jdbcTemplate.update(
            """
            UPDATE after_sale_record
            SET status = 'REJECTED', reject_reason = ?, audit_time = NOW(), updated_at = NOW()
            WHERE id = ?
            """,
            request.getRejectReason(),
            afterSaleId
        );

        // 5. 恢复订单状态
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = ?, updated_at = NOW()
            WHERE id = ?
            """,
            originalStatus,
            record.orderId()
        );

        logger.info("退款审核拒绝, afterSaleId: {}, refundNo: {}, reason: {}",
                afterSaleId, record.refundNo(), request.getRejectReason());

        return getRefundResponse(afterSaleId);
    }

    /**
     * 执行退款
     * 状态: REFUNDING -> REFUNDED (成功) 或 REFUND_FAILED (失败)
     * 库存回补: 已支付订单退款成功后回补库存
     */
    @Transactional
    public SimpleActionResponse processRefund(Long afterSaleId, ProcessRefundRequest request) {
        // 1. 查询售后记录（带锁）
        AfterSaleRecord record = getAfterSaleForUpdate(afterSaleId);

        // 2. 验证状态
        if (!"REFUNDING".equals(record.status())) {
            throw new BusinessException("AFTER_SALE_STATUS_INVALID", "当前状态不可执行退款: " + record.status());
        }

        // 3. 模拟调用微信支付退款接口（幂等性处理）
        // 实际项目中这里应该调用微信支付API
        boolean refundSuccess = mockWechatRefund(record, request.getRefundId());

        if (refundSuccess) {
            // 4a. 退款成功，回补库存
            inventoryService.rollbackConfirmedMaterials(record.orderId());

            // 5a. 更新售后记录状态
            jdbcTemplate.update(
                """
                UPDATE after_sale_record
                SET status = 'REFUNDED', transaction_id = ?, refund_time = NOW(), updated_at = NOW()
                WHERE id = ?
                """,
                request.getRefundId(),
                afterSaleId
            );

            // 6a. 更新订单状态
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'REFUNDED', cancel_time = NOW(), updated_at = NOW()
                WHERE id = ?
                """,
                record.orderId()
            );

            logger.info("退款处理成功, afterSaleId: {}, refundNo: {}, transactionId: {}",
                    afterSaleId, record.refundNo(), request.getRefundId());

            return new SimpleActionResponse(record.orderNo(), "REFUNDED", "退款成功，库存已回补");
        } else {
            // 4b. 退款失败
            jdbcTemplate.update(
                """
                UPDATE after_sale_record
                SET status = 'REFUND_FAILED', updated_at = NOW()
                WHERE id = ?
                """,
                afterSaleId
            );

            // 5b. 订单保持REFUNDING状态，等待人工处理
            logger.warn("退款处理失败, afterSaleId: {}, refundNo: {}", afterSaleId, record.refundNo());

            return new SimpleActionResponse(record.orderNo(), "REFUND_FAILED", "退款处理失败，请人工处理");
        }
    }

    /**
     * 查询售后记录列表（商家端）
     */
    public List<RefundResponse> listAfterSales(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));

        String sql;
        Object[] params;

        if (status != null && !status.isBlank()) {
            sql = """
                SELECT id, refund_no, order_no, order_id, refund_amount, reason, description,
                       evidence_images, status, reject_reason, transaction_id,
                       apply_time, audit_time, refund_time, created_at, updated_at
                FROM after_sale_record
                WHERE status = ?
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit);
            params = new Object[]{status.trim().toUpperCase()};
        } else {
            sql = """
                SELECT id, refund_no, order_no, order_id, refund_amount, reason, description,
                       evidence_images, status, reject_reason, transaction_id,
                       apply_time, audit_time, refund_time, created_at, updated_at
                FROM after_sale_record
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit);
            params = new Object[]{};
        }

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RefundResponse(
            rs.getLong("id"),
            rs.getString("refund_no"),
            rs.getString("order_no"),
            rs.getLong("order_id"),
            rs.getBigDecimal("refund_amount"),
            rs.getString("reason"),
            rs.getString("description"),
            rs.getString("evidence_images"),
            rs.getString("status"),
            rs.getString("reject_reason"),
            rs.getString("transaction_id"),
            toLocalDateTime(rs.getTimestamp("apply_time")),
            toLocalDateTime(rs.getTimestamp("audit_time")),
            toLocalDateTime(rs.getTimestamp("refund_time")),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at"))
        ), params);
    }

    // ===== 内部辅助方法 =====

    private boolean isRefundableStatus(String status) {
        return "PAID".equals(status) || "CONFIRMED".equals(status) || "COMPLETED".equals(status);
    }

    private boolean hasPendingAfterSale(Long orderId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1) FROM after_sale_record
            WHERE order_id = ? AND status IN ('REFUND_REQUESTED', 'REFUNDING')
            """,
            Integer.class,
            orderId
        );
        return count != null && count > 0;
    }

    private Long insertAfterSaleRecord(OrderSnapshot order, String refundNo, RefundRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO after_sale_record(refund_no, order_no, order_id, refund_amount, reason,
                    description, evidence_images, original_order_status, status, apply_time, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'REFUND_REQUESTED', NOW(), NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, refundNo);
            ps.setString(2, order.orderNo());
            ps.setLong(3, order.id());
            ps.setBigDecimal(4, request.getRefundAmount());
            ps.setString(5, request.getReason());
            ps.setString(6, request.getDescription());
            ps.setString(7, request.getEvidenceImages());
            ps.setString(8, order.status());
            return ps;
        }, keyHolder);

        if (updated == 0 || keyHolder.getKey() == null) {
            throw new BusinessException("CREATE_REFUND_FAILED", "创建退款申请失败");
        }
        return keyHolder.getKey().longValue();
    }

    private RefundResponse getRefundResponse(Long afterSaleId) {
        List<RefundResponse> records = jdbcTemplate.query(
            """
            SELECT id, refund_no, order_no, order_id, refund_amount, reason, description,
                   evidence_images, status, reject_reason, transaction_id,
                   apply_time, audit_time, refund_time, created_at, updated_at
            FROM after_sale_record
            WHERE id = ?
            """,
            (rs, rowNum) -> new RefundResponse(
                rs.getLong("id"),
                rs.getString("refund_no"),
                rs.getString("order_no"),
                rs.getLong("order_id"),
                rs.getBigDecimal("refund_amount"),
                rs.getString("reason"),
                rs.getString("description"),
                rs.getString("evidence_images"),
                rs.getString("status"),
                rs.getString("reject_reason"),
                rs.getString("transaction_id"),
                toLocalDateTime(rs.getTimestamp("apply_time")),
                toLocalDateTime(rs.getTimestamp("audit_time")),
                toLocalDateTime(rs.getTimestamp("refund_time")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at"))
            ),
            afterSaleId
        );

        if (records.isEmpty()) {
            throw new BusinessException("AFTER_SALE_NOT_FOUND", "售后记录不存在: " + afterSaleId);
        }
        return records.get(0);
    }

    private AfterSaleRecord getAfterSaleForUpdate(Long afterSaleId) {
        List<AfterSaleRecord> records = jdbcTemplate.query(
            """
            SELECT id, refund_no, order_no, order_id, refund_amount, status, original_order_status
            FROM after_sale_record
            WHERE id = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> new AfterSaleRecord(
                rs.getLong("id"),
                rs.getString("refund_no"),
                rs.getString("order_no"),
                rs.getLong("order_id"),
                rs.getBigDecimal("refund_amount"),
                rs.getString("status"),
                rs.getString("original_order_status")
            ),
            afterSaleId
        );

        if (records.isEmpty()) {
            throw new BusinessException("AFTER_SALE_NOT_FOUND", "售后记录不存在: " + afterSaleId);
        }
        return records.get(0);
    }

    private OrderSnapshot getOrderForUpdate(String orderNo) {
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount,
                   receiver_name, receiver_phone, receiver_address,
                   created_at, pay_time, cancel_time, lock_expire_at, remark
            FROM customer_order
            WHERE order_no = ?
            FOR UPDATE
            """,
            (rs, rowNum) -> new OrderSnapshot(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("user_id"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("payment_amount"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("pay_time")),
                toLocalDateTime(rs.getTimestamp("cancel_time")),
                toLocalDateTime(rs.getTimestamp("lock_expire_at")),
                rs.getString("remark")
            ),
            orderNo
        );

        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + orderNo);
        }
        return orders.get(0);
    }

    private OrderSnapshot getOrderByNo(String orderNo) {
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount,
                   receiver_name, receiver_phone, receiver_address,
                   created_at, pay_time, cancel_time, lock_expire_at, remark
            FROM customer_order
            WHERE order_no = ?
            """,
            (rs, rowNum) -> new OrderSnapshot(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getLong("user_id"),
                rs.getString("status"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("payment_amount"),
                rs.getString("receiver_name"),
                rs.getString("receiver_phone"),
                rs.getString("receiver_address"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("pay_time")),
                toLocalDateTime(rs.getTimestamp("cancel_time")),
                toLocalDateTime(rs.getTimestamp("lock_expire_at")),
                rs.getString("remark")
            ),
            orderNo
        );

        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + orderNo);
        }
        return orders.get(0);
    }

    private String resolveOriginalStatus(AfterSaleRecord record) {
        if (record.originalOrderStatus() != null && !record.originalOrderStatus().isBlank()) {
            return record.originalOrderStatus();
        }
        return determineOriginalStatus(record.orderId());
    }

    private String determineOriginalStatus(Long orderId) {
        String status = jdbcTemplate.queryForObject(
            """
            SELECT CASE
                     WHEN completed_at IS NOT NULL THEN 'COMPLETED'
                     WHEN shipped_at IS NOT NULL THEN 'CONFIRMED'
                     WHEN pay_time IS NOT NULL THEN 'PAID'
                     ELSE 'CONFIRMED'
                   END AS original_status
            FROM customer_order
            WHERE id = ?
            """,
            String.class,
            orderId
        );
        return status == null || status.isBlank() ? "CONFIRMED" : status;
    }

    private boolean mockWechatRefund(AfterSaleRecord record, String refundId) {
        // 模拟微信支付退款
        // 实际项目中这里应该调用微信支付API
        // 假设退款总是成功（幂等性由微信支付保证）
        logger.info("模拟调用微信支付退款, refundNo: {}, refundId: {}", record.refundNo(), refundId);
        return true;
    }

    private static String generateRefundNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "RF" + timePart + randomPart;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    // ===== 内部记录类 =====

    private record OrderSnapshot(
        Long id,
        String orderNo,
        Long userId,
        String status,
        BigDecimal totalAmount,
        BigDecimal paymentAmount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        LocalDateTime createdAt,
        LocalDateTime payTime,
        LocalDateTime cancelTime,
        LocalDateTime lockExpireAt,
        String remark
    ) {
    }

    private record AfterSaleRecord(
        Long id,
        String refundNo,
        String orderNo,
        Long orderId,
        BigDecimal refundAmount,
        String status,
        String originalOrderStatus
    ) {
    }
}
