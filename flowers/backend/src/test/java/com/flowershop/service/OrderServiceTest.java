package com.flowershop.service;

import com.flowershop.dto.CancelOrderRequest;
import com.flowershop.dto.ConfirmOrderRequest;
import com.flowershop.dto.CreateOrderRequest;
import com.flowershop.dto.CreateOrderResponse;
import com.flowershop.dto.OrderDetailResponse;
import com.flowershop.dto.PayOrderRequest;
import com.flowershop.dto.SimpleActionResponse;
import com.flowershop.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProductService productService;

    @Mock
    private InventoryService inventoryService;

    private OrderService createOrderService() {
        return new OrderService(
            jdbcTemplate,
            productService,
            inventoryService,
            new UserPointsService(jdbcTemplate),
            new MemberBenefitsService(jdbcTemplate)
        );
    }

    @Test
    void payOrderAwardsMemberPointsFromPaymentAmount() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot("ORD-1001", 11L, 7L, "LOCKED", new BigDecimal("88.80"));
        when(jdbcTemplate.queryForObject(
            "SELECT config_value FROM system_config WHERE config_key = 'points_per_yuan'",
            String.class
        )).thenReturn("1");

        PayOrderRequest request = new PayOrderRequest();
        request.setPaymentChannel("WECHAT_MINI");
        request.setPaymentNo("PAY-1001");

        SimpleActionResponse response = orderService.payOrder("ORD-1001", request);

        assertEquals("PAID", response.status());
        verify(inventoryService).confirmLockedMaterials(11L);
        verify(jdbcTemplate).update(contains("UPDATE user_customer SET points = COALESCE(points, 0) + ?"), eq(88), eq(7L));
    }

    @Test
    void cancelOrderRollsBackAwardedPointsForRefundedOrder() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot("ORD-1002", 12L, 9L, "PAID", new BigDecimal("88.80"));
        when(jdbcTemplate.queryForObject(
            "SELECT config_value FROM system_config WHERE config_key = 'points_per_yuan'",
            String.class
        )).thenReturn("1");

        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason("用户取消");

        SimpleActionResponse response = orderService.cancelOrder("ORD-1002", request);

        assertEquals("REFUNDED", response.status());
        verify(inventoryService).rollbackConfirmedMaterials(12L);
        verify(jdbcTemplate).update(contains("UPDATE user_customer SET points = GREATEST(COALESCE(points, 0) - ?, 0)"), eq(88), eq(9L));
    }

    @Test
    void confirmOrderPersistsProvidedLogisticsInfo() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot("ORD-2001", 21L, 7L, "PAID", new BigDecimal("188.00"));

        orderService.confirmOrder(
            "ORD-2001",
            new ConfirmOrderRequest("顺丰速运", "SF202603090001")
        );

        verify(jdbcTemplate).update(
            contains("tracking_company = COALESCE(?, tracking_company)"),
            eq("顺丰速运"),
            eq("SF202603090001"),
            eq(21L)
        );
    }

    @Test
    void completeOrderRequiresConfirmedStatus() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot("ORD-2002", 22L, 7L, "PAID", new BigDecimal("188.00"));

        assertThrows(BusinessException.class, () -> orderService.completeOrder("ORD-2002"));
    }

    @Test
    void getOrderDetailFallsBackToUserNameWhenReceiverNameIsInvalid() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot(
            "ORD-2004",
            24L,
            1L,
            "LOCKED",
            new BigDecimal("91.20"),
            "?????",
            null,
            null,
            null
        );
        when(jdbcTemplate.queryForObject(
            "SELECT name FROM user_customer WHERE id = ?",
            String.class,
            1L
        )).thenReturn("林小雨");
        when(jdbcTemplate.query(contains("FROM order_item"), any(RowMapper.class), eq(24L))).thenReturn(List.of());
        when(jdbcTemplate.query(contains("FROM stock_lock"), any(RowMapper.class), eq(24L))).thenReturn(List.of());

        OrderDetailResponse response = orderService.getOrderDetail("ORD-2004");

        assertEquals("林小雨", response.receiverName());
    }

    @Test
    void getOrderDetailIncludesOrderIdForReviewSubmission() throws Exception {
        OrderService orderService = createOrderService();
        stubOrderSnapshot(
            "ORD-2003",
            23L,
            10L,
            "COMPLETED",
            new BigDecimal("128.00")
        );
        when(jdbcTemplate.query(contains("FROM order_item"), any(RowMapper.class), eq(23L))).thenReturn(List.of());
        when(jdbcTemplate.query(contains("FROM stock_lock"), any(RowMapper.class), eq(23L))).thenReturn(List.of());

        OrderDetailResponse response = orderService.getOrderDetail("ORD-2003");

        assertEquals(23L, response.id());
        assertEquals("ORD-2003", response.orderNo());
    }
    @Test
    void getOrderDetailReturnsLogisticsFields() throws Exception {
        OrderService orderService = createOrderService();
        LocalDateTime shippedAt = LocalDateTime.of(2026, 3, 9, 18, 30);
        stubOrderSnapshot(
            "ORD-2002",
            22L,
            8L,
            "CONFIRMED",
            new BigDecimal("168.00"),
            "京东物流",
            "JD202603090002",
            shippedAt
        );
        when(jdbcTemplate.query(contains("FROM order_item"), any(RowMapper.class), eq(22L))).thenReturn(List.of());
        when(jdbcTemplate.query(contains("FROM stock_lock"), any(RowMapper.class), eq(22L))).thenReturn(List.of());

        OrderDetailResponse response = orderService.getOrderDetail("ORD-2002");

        assertEquals("京东物流", response.trackingCompany());
        assertEquals("JD202603090002", response.trackingNo());
        assertEquals(shippedAt, response.shippedAt());
    }

    @Test
    void createOrderAppliesMemberDiscountToPaymentAmount() {
        OrderService orderService = createOrderService();
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM user_customer WHERE id = ?", Integer.class, 7L)).thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT points FROM user_customer WHERE id = ?", Integer.class, 7L)).thenReturn(1000);
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(new HashMap<>(Map.of("id", 31L)));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
        when(productService.getProductSnapshot(5L)).thenReturn(new ProductService.ProductSnapshot(
            5L,
            "玫瑰花束",
            "BOUQUET",
            "DAILY",
            "",
            null,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            "官方花店",
            "ON_SALE"
        ));
        when(productService.calculateAutoUnitPrice(5L)).thenReturn(new BigDecimal("100.00"));
        when(productService.getBomDemands(5L)).thenReturn(List.of(new ProductService.BomDemand(101L, BigDecimal.ONE)));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(7L);
        request.setDeliveryFee(new BigDecimal("8.00"));
        request.setDeliveryMode("STANDARD");
        request.setDeliverySlot("ASAP");
        CreateOrderRequest.OrderLine line = new CreateOrderRequest.OrderLine();
        line.setProductId(5L);
        line.setQuantity(1);
        request.setItems(List.of(line));

        CreateOrderResponse response = orderService.createOrder(request);

        assertEquals(new BigDecimal("103.00"), response.totalAmount());
        assertEquals(1, response.orders().size());
        verify(jdbcTemplate).update(
            contains("delivery_fee = ?"),
            eq(new BigDecimal("103.00")),
            eq(new BigDecimal("103.00")),
            eq(new BigDecimal("8.00")),
            eq("STANDARD"),
            eq("ASAP"),
            eq(31L)
        );
    }

    @Test
    void createOrderSplitsOrdersByMerchantAccount() {
        OrderService orderService = createOrderService();
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM user_customer WHERE id = ?", Integer.class, 7L)).thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT points FROM user_customer WHERE id = ?", Integer.class, 7L)).thenReturn(0);

        final long[] orderIds = { 41L };
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(new HashMap<>(Map.of("id", orderIds[0]++)));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        when(productService.getProductSnapshot(5L)).thenReturn(new ProductService.ProductSnapshot(
            5L,
            "A鑺辨潫",
            "BOUQUET",
            "DAILY",
            "",
            null,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "merchant_demo_01",
            "鏄熸渤鑺卞潑",
            "ON_SALE"
        ));
        when(productService.getProductSnapshot(6L)).thenReturn(new ProductService.ProductSnapshot(
            6L,
            "B鑺辨潫",
            "BOUQUET",
            "DAILY",
            "",
            null,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "merchant_demo_02",
            "鏄ュ洯鑺辫壓",
            "ON_SALE"
        ));
        when(productService.calculateAutoUnitPrice(5L)).thenReturn(new BigDecimal("100.00"));
        when(productService.calculateAutoUnitPrice(6L)).thenReturn(new BigDecimal("100.00"));
        when(productService.getBomDemands(5L)).thenReturn(List.of(new ProductService.BomDemand(101L, BigDecimal.ONE)));
        when(productService.getBomDemands(6L)).thenReturn(List.of(new ProductService.BomDemand(102L, BigDecimal.ONE)));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserId(7L);
        request.setDeliveryFee(new BigDecimal("8.00"));
        request.setDeliveryMode("STANDARD");
        request.setDeliverySlot("ASAP");

        CreateOrderRequest.OrderLine line1 = new CreateOrderRequest.OrderLine();
        line1.setProductId(5L);
        line1.setQuantity(1);
        CreateOrderRequest.OrderLine line2 = new CreateOrderRequest.OrderLine();
        line2.setProductId(6L);
        line2.setQuantity(1);
        request.setItems(List.of(line1, line2));

        CreateOrderResponse response = orderService.createOrder(request);

        assertEquals(new BigDecimal("208.00"), response.totalAmount());
        assertEquals(2, response.orders().size());
        assertEquals(new BigDecimal("104.00"), response.orders().get(0).totalAmount());
        assertEquals(new BigDecimal("104.00"), response.orders().get(1).totalAmount());

        verify(jdbcTemplate, times(2)).update(contains("delivery_fee = ?"), any(), any(), any(), any(), any(), any());
        verify(jdbcTemplate).update(
            contains("delivery_fee = ?"),
            eq(new BigDecimal("104.00")),
            eq(new BigDecimal("104.00")),
            eq(new BigDecimal("4.00")),
            eq("STANDARD"),
            eq("ASAP"),
            eq(41L)
        );
        verify(jdbcTemplate).update(
            contains("delivery_fee = ?"),
            eq(new BigDecimal("104.00")),
            eq(new BigDecimal("104.00")),
            eq(new BigDecimal("4.00")),
            eq("STANDARD"),
            eq("ASAP"),
            eq(42L)
        );
    }

    private void stubOrderSnapshot(String orderNo, Long orderId, Long userId, String status, BigDecimal paymentAmount) throws Exception {
        stubOrderSnapshot(orderNo, orderId, userId, status, paymentAmount, null, null, null);
    }

    private void stubOrderSnapshot(
        String orderNo,
        Long orderId,
        Long userId,
        String status,
        BigDecimal paymentAmount,
        String receiverName,
        String trackingCompany,
        String trackingNo,
        LocalDateTime shippedAt
    ) throws Exception {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            LocalDateTime now = LocalDateTime.now();

            when(rs.getLong("id")).thenReturn(orderId);
            when(rs.getString("order_no")).thenReturn(orderNo);
            when(rs.getLong("user_id")).thenReturn(userId);
            when(rs.getString("status")).thenReturn(status);
            when(rs.getBigDecimal("total_amount")).thenReturn(paymentAmount);
            when(rs.getBigDecimal("payment_amount")).thenReturn(paymentAmount);
            when(rs.getString("receiver_name")).thenReturn(receiverName);
            when(rs.getString("receiver_phone")).thenReturn("13800138000");
            when(rs.getString("receiver_address")).thenReturn("Shanghai");
            when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(now.minusHours(1)));
            when(rs.getTimestamp("pay_time")).thenReturn(null);
            when(rs.getTimestamp("cancel_time")).thenReturn(null);
            when(rs.getTimestamp("lock_expire_at")).thenReturn(Timestamp.valueOf(now.plusHours(1)));
            when(rs.getString("tracking_company")).thenReturn(trackingCompany);
            when(rs.getString("tracking_no")).thenReturn(trackingNo);
            when(rs.getTimestamp("shipped_at")).thenReturn(shippedAt == null ? null : Timestamp.valueOf(shippedAt));
            when(rs.getString("remark")).thenReturn(null);

            return List.of(rowMapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(contains("FROM customer_order"), any(RowMapper.class), eq(orderNo));
    }

    private void stubOrderSnapshot(
        String orderNo,
        Long orderId,
        Long userId,
        String status,
        BigDecimal paymentAmount,
        String trackingCompany,
        String trackingNo,
        LocalDateTime shippedAt
    ) throws Exception {
        stubOrderSnapshot(orderNo, orderId, userId, status, paymentAmount, "Alice", trackingCompany, trackingNo, shippedAt);
    }
}

