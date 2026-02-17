package com.flowershop.service;

import com.flowershop.dto.CancelOrderRequest;
import com.flowershop.dto.CreateOrderRequest;
import com.flowershop.dto.CreateOrderResponse;
import com.flowershop.dto.OrderDetailResponse;
import com.flowershop.dto.OrderItemResponse;
import com.flowershop.dto.OrderStockLockResponse;
import com.flowershop.dto.OrderSummaryResponse;
import com.flowershop.dto.PayOrderRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OrderService {

    private static final int FALLBACK_LOCK_MINUTES = 30;

    private final JdbcTemplate jdbcTemplate;
    private final ProductService productService;
    private final InventoryService inventoryService;

    public OrderService(JdbcTemplate jdbcTemplate, ProductService productService, InventoryService inventoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.productService = productService;
        this.inventoryService = inventoryService;
    }

    private int getLockMinutes() {
        try {
            String val = jdbcTemplate.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = 'order_lock_minutes'",
                String.class
            );
            return val != null ? Integer.parseInt(val) : FALLBACK_LOCK_MINUTES;
        } catch (Exception e) {
            return FALLBACK_LOCK_MINUTES;
        }
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        ensureUserExists(request.getUserId());

        String orderNo = generateOrderNo();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockExpireAt = now.plusMinutes(getLockMinutes());
        String remark = request.getRemark();
        Long orderId = insertOrder(orderNo, request.getUserId(), remark, lockExpireAt,
                request.getReceiverName(), request.getReceiverPhone(), request.getReceiverAddress());

        BigDecimal itemAmount = BigDecimal.ZERO;
        Map<Long, BigDecimal> flowerDemand = new LinkedHashMap<>();

        for (CreateOrderRequest.OrderLine line : request.getItems()) {
            ProductService.ProductSnapshot product = productService.getProductSnapshot(line.getProductId());
            BigDecimal unitPrice = productService.calculateAutoUnitPrice(product.id());
            BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(line.getQuantity()));

            insertOrderItem(orderId, product.id(), product.title(), unitPrice, line.getQuantity(), lineAmount);
            itemAmount = itemAmount.add(lineAmount);

            List<ProductService.BomDemand> demands = productService.getBomDemands(product.id());
            if (demands.isEmpty()) {
                throw new BusinessException("BOM_NOT_FOUND", "商品缺少BOM配置，无法扣减库存: " + product.title());
            }
            for (ProductService.BomDemand demand : demands) {
                BigDecimal needQty = demand.dosage().multiply(BigDecimal.valueOf(line.getQuantity()));
                flowerDemand.merge(demand.flowerId(), needQty, BigDecimal::add);
            }
        }

        BigDecimal totalAmount = itemAmount
            .add(safeAmount(request.getPackagingFee()))
            .add(safeAmount(request.getDeliveryFee()))
            .setScale(2, RoundingMode.HALF_UP);

        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET total_amount = ?, payment_amount = ?, status = 'LOCKED', updated_at = NOW()
            WHERE id = ?
            """,
            totalAmount,
            totalAmount,
            orderId
        );

        inventoryService.lockMaterials(orderId, orderNo, flowerDemand, lockExpireAt);
        return new CreateOrderResponse(orderNo, "LOCKED", totalAmount, lockExpireAt);
    }

    public OrderDetailResponse getOrderDetail(String orderNo) {
        OrderSnapshot snapshot = getOrderByNo(orderNo);

        List<OrderItemResponse> items = jdbcTemplate.query(
            """
            SELECT product_id, product_title, unit_price, quantity, line_amount
            FROM order_item
            WHERE order_id = ?
            ORDER BY id
            """,
            (rs, rowNum) -> new OrderItemResponse(
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_amount")
            ),
            snapshot.id()
        );

        List<OrderStockLockResponse> locks = jdbcTemplate.query(
            """
            SELECT sl.flower_id, f.name AS flower_name, ROUND(SUM(sl.lock_qty), 2) AS lock_qty,
                   MAX(sl.status) AS lock_status
            FROM stock_lock sl
            JOIN flower_material f ON sl.flower_id = f.id
            WHERE sl.order_id = ?
            GROUP BY sl.flower_id, f.name
            ORDER BY sl.flower_id
            """,
            (rs, rowNum) -> new OrderStockLockResponse(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getBigDecimal("lock_qty"),
                rs.getString("lock_status")
            ),
            snapshot.id()
        );

        return new OrderDetailResponse(
            snapshot.orderNo(),
            snapshot.userId(),
            snapshot.status(),
            snapshot.totalAmount(),
            snapshot.paymentAmount(),
            snapshot.receiverName(),
            snapshot.receiverPhone(),
            snapshot.receiverAddress(),
            snapshot.createdAt(),
            snapshot.payTime(),
            snapshot.cancelTime(),
            snapshot.lockExpireAt(),
            snapshot.remark(),
            items,
            locks
        );
    }

    public List<String> listOrderNosByUser(Long userId, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        String sql = """
            SELECT order_no
            FROM customer_order
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT %d
            """.formatted(safeLimit);

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getString("order_no"),
            userId
        );
    }

    public List<OrderSummaryResponse> listOrderDetailsByUser(Long userId, Integer limit) {
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<OrderSnapshot> orders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount,
                   receiver_name, receiver_phone, receiver_address,
                   created_at, pay_time, cancel_time, lock_expire_at, remark
            FROM customer_order
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT %d
            """.formatted(safeLimit),
            this::mapOrderSnapshot,
            userId
        );

        if (orders.isEmpty()) {
            return List.of();
        }

        return buildOrderSummaries(orders);
    }

    /** 商家端：按状态查询所有订单 */
    public List<OrderSummaryResponse> listAllOrders(String status, Integer limit) {
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<OrderSnapshot> orders;

        if (status != null && !status.isBlank()) {
            orders = jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, status, total_amount, payment_amount,
                       receiver_name, receiver_phone, receiver_address,
                       created_at, pay_time, cancel_time, lock_expire_at, remark
                FROM customer_order
                WHERE status = ?
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit),
                this::mapOrderSnapshot,
                status.trim().toUpperCase()
            );
        } else {
            orders = jdbcTemplate.query(
                """
                SELECT id, order_no, user_id, status, total_amount, payment_amount,
                       receiver_name, receiver_phone, receiver_address,
                       created_at, pay_time, cancel_time, lock_expire_at, remark
                FROM customer_order
                ORDER BY created_at DESC
                LIMIT %d
                """.formatted(safeLimit),
                this::mapOrderSnapshot
            );
        }

        if (orders.isEmpty()) {
            return List.of();
        }
        return buildOrderSummaries(orders);
    }

    @Transactional
    public SimpleActionResponse payOrder(String orderNo, PayOrderRequest request) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("PAID".equals(order.status()) || "COMPLETED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, order.status(), "订单已支付");
        }
        if (!"LOCKED".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可支付: " + order.status());
        }

        if (order.lockExpireAt() != null && order.lockExpireAt().isBefore(LocalDateTime.now())) {
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED',
                    cancel_time = NOW(),
                    remark = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), "支付超时自动取消"),
                order.id()
            );
            throw new BusinessException("ORDER_EXPIRED", "订单超时未支付，库存已释放");
        }

        inventoryService.confirmLockedMaterials(order.id());
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'PAID', pay_time = NOW(), payment_channel = ?, payment_no = ?, updated_at = NOW()
            WHERE id = ?
            """,
            request.getPaymentChannel(),
            request.getPaymentNo(),
            order.id()
        );

        return new SimpleActionResponse(orderNo, "PAID", "支付成功并完成库存扣减");
    }

    @Transactional
    public SimpleActionResponse confirmOrder(String orderNo) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("CONFIRMED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, "CONFIRMED", "订单已确认");
        }
        if (!"PAID".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可确认: " + order.status());
        }
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'CONFIRMED', updated_at = NOW()
            WHERE id = ?
            """,
            order.id()
        );
        return new SimpleActionResponse(orderNo, "CONFIRMED", "订单已确认发货");
    }

    @Transactional
    public SimpleActionResponse completeOrder(String orderNo) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        if ("COMPLETED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, "COMPLETED", "订单已完成");
        }
        if (!"PAID".equals(order.status()) && !"CONFIRMED".equals(order.status())) {
            throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可完成: " + order.status());
        }
        jdbcTemplate.update(
            """
            UPDATE customer_order
            SET status = 'COMPLETED', updated_at = NOW()
            WHERE id = ?
            """,
            order.id()
        );
        return new SimpleActionResponse(orderNo, "COMPLETED", "订单已完成");
    }

    @Transactional
    public SimpleActionResponse cancelOrder(String orderNo, CancelOrderRequest request) {
        OrderSnapshot order = getOrderForUpdate(orderNo);
        String reason = request == null || request.getReason() == null || request.getReason().isBlank()
            ? "用户取消"
            : request.getReason().trim();

        if ("CANCELLED".equals(order.status()) || "REFUNDED".equals(order.status())) {
            return new SimpleActionResponse(orderNo, order.status(), "订单已取消或已退款");
        }

        if ("LOCKED".equals(order.status()) || "CREATED".equals(order.status())) {
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED', cancel_time = NOW(), remark = ?, updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), reason),
                order.id()
            );
            return new SimpleActionResponse(orderNo, "CANCELLED", "取消成功，锁库存已回滚");
        }

        if ("PAID".equals(order.status()) || "CONFIRMED".equals(order.status()) || "COMPLETED".equals(order.status())) {
            inventoryService.rollbackConfirmedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'REFUNDED', cancel_time = NOW(), remark = ?, updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), reason),
                order.id()
            );
            return new SimpleActionResponse(orderNo, "REFUNDED", "退款成功，库存已回补");
        }

        throw new BusinessException("ORDER_STATUS_INVALID", "当前状态不可取消: " + order.status());
    }

    @Transactional
    public int releaseExpiredOrders() {
        List<OrderSnapshot> expiredOrders = jdbcTemplate.query(
            """
            SELECT id, order_no, user_id, status, total_amount, payment_amount,
                   receiver_name, receiver_phone, receiver_address,
                   created_at, pay_time, cancel_time, lock_expire_at, remark
            FROM customer_order
            WHERE status = 'LOCKED' AND lock_expire_at < NOW()
            FOR UPDATE
            """,
            this::mapOrderSnapshot
        );

        for (OrderSnapshot order : expiredOrders) {
            inventoryService.releaseLockedMaterials(order.id());
            jdbcTemplate.update(
                """
                UPDATE customer_order
                SET status = 'CANCELLED',
                    cancel_time = NOW(),
                    remark = ?,
                    updated_at = NOW()
                WHERE id = ?
                """,
                appendRemark(order.remark(), "超时未支付自动取消"),
                order.id()
            );
        }
        return expiredOrders.size();
    }

    // ===== 内部辅助方法 =====

    private void ensureUserExists(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_customer WHERE id = ?",
            Integer.class,
            userId
        );
        if (count == null || count == 0) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在: " + userId);
        }
    }

    private Long insertOrder(String orderNo, Long userId, String remark, LocalDateTime lockExpireAt,
                             String receiverName, String receiverPhone, String receiverAddress) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO customer_order(order_no, user_id, total_amount, payment_amount, status,
                    receiver_name, receiver_phone, receiver_address, lock_expire_at, remark, created_at, updated_at)
                VALUES (?, ?, 0, 0, 'CREATED', ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            ps.setString(3, receiverName);
            ps.setString(4, receiverPhone);
            ps.setString(5, receiverAddress);
            ps.setTimestamp(6, Timestamp.valueOf(lockExpireAt));
            ps.setString(7, remark);
            return ps;
        }, keyHolder);

        if (updated == 0 || keyHolder.getKey() == null) {
            throw new BusinessException("CREATE_ORDER_FAILED", "创建订单失败");
        }
        return keyHolder.getKey().longValue();
    }

    private void insertOrderItem(Long orderId, Long productId, String productTitle, BigDecimal unitPrice, Integer quantity, BigDecimal lineAmount) {
        jdbcTemplate.update(
            """
            INSERT INTO order_item(order_id, product_id, product_title, unit_price, quantity, line_amount, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            orderId,
            productId,
            productTitle,
            unitPrice,
            quantity,
            lineAmount
        );
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
            this::mapOrderSnapshot,
            orderNo
        );
        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + orderNo);
        }
        return orders.get(0);
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
            this::mapOrderSnapshot,
            orderNo
        );
        if (orders.isEmpty()) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + orderNo);
        }
        return orders.get(0);
    }

    private OrderSnapshot mapOrderSnapshot(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new OrderSnapshot(
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
        );
    }

    private List<OrderSummaryResponse> buildOrderSummaries(List<OrderSnapshot> orders) {
        List<Long> orderIds = orders.stream().map(OrderSnapshot::id).toList();
        String placeholders = String.join(",", Collections.nCopies(orderIds.size(), "?"));
        String itemSql = """
            SELECT order_id, product_id, product_title, unit_price, quantity, line_amount
            FROM order_item
            WHERE order_id IN (%s)
            ORDER BY order_id DESC, id ASC
            """.formatted(placeholders);

        List<OrderItemRow> itemRows = jdbcTemplate.query(
            itemSql,
            (rs, rowNum) -> new OrderItemRow(
                rs.getLong("order_id"),
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_amount")
            ),
            orderIds.toArray()
        );

        Map<Long, List<OrderItemResponse>> itemMap = new LinkedHashMap<>();
        for (OrderSnapshot order : orders) {
            itemMap.put(order.id(), new ArrayList<>());
        }
        for (OrderItemRow row : itemRows) {
            List<OrderItemResponse> list = itemMap.computeIfAbsent(row.orderId(), key -> new ArrayList<>());
            list.add(new OrderItemResponse(
                row.productId(),
                row.productTitle(),
                row.unitPrice(),
                row.quantity(),
                row.lineAmount()
            ));
        }

        return orders.stream().map(order -> new OrderSummaryResponse(
            order.orderNo(),
            order.status(),
            order.totalAmount(),
            order.paymentAmount(),
            order.receiverName(),
            order.receiverPhone(),
            order.receiverAddress(),
            order.createdAt(),
            order.payTime(),
            order.cancelTime(),
            order.lockExpireAt(),
            order.remark(),
            itemMap.getOrDefault(order.id(), List.of())
        )).toList();
    }

    private static String appendRemark(String oldRemark, String appendText) {
        if (appendText == null || appendText.isBlank()) {
            return oldRemark;
        }
        if (oldRemark == null || oldRemark.isBlank()) {
            return appendText;
        }
        return oldRemark + " | " + appendText;
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String generateOrderNo() {
        String timePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "FO" + timePart + randomPart;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record OrderItemRow(
        Long orderId,
        Long productId,
        String productTitle,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineAmount
    ) {
    }

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
