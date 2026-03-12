package com.flowershop.service;

import com.flowershop.dto.PaymentCallbackRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付服务
 * 处理微信支付回调、签名验证、订单状态更新
 */
@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final UserPointsService userPointsService;

    @Value("${wechat.pay.api-key:}")
    private String wechatApiKey;

    public PaymentService(JdbcTemplate jdbcTemplate,
                          InventoryService inventoryService,
                          UserPointsService userPointsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.userPointsService = userPointsService;
    }

    /**
     * 处理微信支付回调
     * 包含签名验证、幂等性处理、订单状态更新、库存扣减
     */
    @Transactional
    public SimpleActionResponse handlePaymentCallback(PaymentCallbackRequest request) {
        // 1. 验证签名
        if (!verifySignature(request)) {
            logger.warn("支付回调签名验证失败, orderNo: {}", request.getOrderNo());
            throw new BusinessException("SIGNATURE_INVALID", "签名验证失败");
        }

        // 2. 验证支付结果
        if (!"SUCCESS".equals(request.getResultCode())) {
            logger.warn("支付回调结果非成功, orderNo: {}, resultCode: {}",
                    request.getOrderNo(), request.getResultCode());
            throw new BusinessException("PAYMENT_FAILED", "支付未成功");
        }

        String orderNo = request.getOrderNo();

        // 3. 查询订单信息（带锁）
        OrderSnapshot order = getOrderForUpdate(orderNo);

        // 4. 幂等性检查 - 已支付则直接返回成功
        if ("PAID".equals(order.status()) || "COMPLETED".equals(order.status())) {
            logger.info("订单已支付, 幂等返回, orderNo: {}, status: {}", orderNo, order.status());
            return new SimpleActionResponse(orderNo, order.status(), "订单已支付");
        }

        // 5. 验证订单状态
        if (!"LOCKED".equals(order.status())) {
            logger.warn("订单状态不可支付, orderNo: {}, status: {}", orderNo, order.status());
            throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可支付: " + order.status());
        }

        // 6. 验证支付金额
        BigDecimal payAmount = new BigDecimal(request.getTotalFee()).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        if (payAmount.compareTo(order.paymentAmount()) != 0) {
            logger.warn("支付金额不匹配, orderNo: {}, expected: {}, actual: {}",
                    orderNo, order.paymentAmount(), payAmount);
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH", "支付金额不匹配");
        }

        // 7. 检查订单是否超时
        if (order.lockExpireAt() != null && order.lockExpireAt().isBefore(LocalDateTime.now())) {
            logger.warn("订单已超时, orderNo: {}", orderNo);
            // 释放锁库存
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED',
                    cancel_time = NOW(),
                    remark = CONCAT(COALESCE(remark, ''), ' | 支付超时自动取消'),
                    updated_at = NOW()
                WHERE id = ?
                """,
                order.id()
            );
            throw new BusinessException("ORDER_EXPIRED", "订单超时未支付，库存已释放");
        }

        // 8. 执行库存确认扣减
        inventoryService.confirmLockedMaterials(order.id());

        // 9. 更新订单状态为已支付
        LocalDateTime payTime = parsePayTime(request.getTimeEnd());
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'PAID',
                pay_time = ?,
                payment_channel = 'WECHAT_PAY',
                payment_no = ?,
                updated_at = NOW()
            WHERE id = ?
            """,
            payTime,
            request.getTransactionId(),
            order.id()
        );

        // 10. 记录支付流水
        userPointsService.awardPointsForAmount(order.userId(), order.paymentAmount());
        insertPaymentLog(order.id(), orderNo, request, payAmount);

        logger.info("支付回调处理成功, orderNo: {}, transactionId: {}", orderNo, request.getTransactionId());
        return new SimpleActionResponse(orderNo, "PAID", "支付成功并完成库存扣减");
    }

    /**
     * 验证微信支付回调签名
     * 使用HMAC-SHA256算法
     */
    private boolean verifySignature(PaymentCallbackRequest request) {
        // 如果未配置API密钥，跳过签名验证（仅用于开发环境）
        if (wechatApiKey == null || wechatApiKey.isBlank()) {
            logger.warn("微信支付API密钥未配置，跳过签名验证");
            return true;
        }

        try {
            // 构建待签名字符串
            Map<String, String> params = new HashMap<>();
            params.put("orderNo", request.getOrderNo());
            params.put("transactionId", request.getTransactionId());
            params.put("totalFee", request.getTotalFee());
            params.put("resultCode", request.getResultCode());
            params.put("timeEnd", request.getTimeEnd());
            params.put("nonceStr", request.getNonceStr());

            String signString = buildSignString(params);
            String calculatedSign = hmacSha256(signString, wechatApiKey);

            return calculatedSign.equalsIgnoreCase(request.getSign());
        } catch (Exception e) {
            logger.error("签名验证异常", e);
            return false;
        }
    }

    /**
     * 构建签名字符串
     * 按照参数名ASCII码从小到大排序，拼接成key=value&key=value格式
     */
    private String buildSignString(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    /**
     * HMAC-SHA256签名
     */
    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(bytes);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 解析支付时间
     * 微信格式：yyyyMMddHHmmss
     */
    private LocalDateTime parsePayTime(String timeEnd) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            return LocalDateTime.parse(timeEnd, formatter);
        } catch (Exception e) {
            logger.warn("解析支付时间失败: {}, 使用当前时间", timeEnd);
            return LocalDateTime.now();
        }
    }

    /**
     * 查询订单（带锁）
     */
    private OrderSnapshot getOrderForUpdate(String orderNo) {
        var orders = jdbcTemplate.query(
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

    /**
     * 插入支付流水记录
     */
    private void insertPaymentLog(Long orderId, String orderNo, PaymentCallbackRequest request, BigDecimal payAmount) {
        try {
            jdbcTemplate.update(
                """
                INSERT INTO payment_log (order_id, order_no, transaction_id, payment_channel,
                                        pay_amount, result_code, notify_time, created_at)
                VALUES (?, ?, ?, 'WECHAT_PAY', ?, ?, NOW(), NOW())
                """,
                orderId,
                orderNo,
                request.getTransactionId(),
                payAmount,
                request.getResultCode()
            );
        } catch (Exception e) {
            // 支付流水记录失败不影响主流程，仅记录日志
            logger.error("记录支付流水失败, orderNo: {}", orderNo, e);
        }
    }

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 订单快照记录
     */
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
}
