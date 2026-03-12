package com.flowershop.service;

import com.flowershop.dto.PaymentCallbackRequest;
import com.flowershop.dto.SimpleActionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private InventoryService inventoryService;

    @Test
    void paymentCallbackAwardsMemberPointsAfterSuccessfulPayment() throws Exception {
        PaymentService paymentService = new PaymentService(jdbcTemplate, inventoryService, new UserPointsService(jdbcTemplate));
        ReflectionTestUtils.setField(paymentService, "wechatApiKey", "");
        stubOrderSnapshot("ORD-CB-1", 21L, 15L, "LOCKED", new BigDecimal("66.60"));
        when(jdbcTemplate.queryForObject(
            "SELECT config_value FROM system_config WHERE config_key = 'points_per_yuan'",
            String.class
        )).thenReturn("1");

        PaymentCallbackRequest request = new PaymentCallbackRequest();
        request.setOrderNo("ORD-CB-1");
        request.setTransactionId("WX-TXN-1");
        request.setTotalFee("6660");
        request.setResultCode("SUCCESS");
        request.setTimeEnd("20260309123000");
        request.setNonceStr("nonce-1");

        SimpleActionResponse response = paymentService.handlePaymentCallback(request);

        assertEquals("PAID", response.status());
        verify(inventoryService).confirmLockedMaterials(21L);
        verify(jdbcTemplate).update(contains("UPDATE user_customer SET points = COALESCE(points, 0) + ?"), eq(66), eq(15L));
    }

    private void stubOrderSnapshot(String orderNo, Long orderId, Long userId, String status, BigDecimal paymentAmount) throws Exception {
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
            when(rs.getString("receiver_name")).thenReturn("Alice");
            when(rs.getString("receiver_phone")).thenReturn("13800138000");
            when(rs.getString("receiver_address")).thenReturn("Shanghai");
            when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(now.minusHours(1)));
            when(rs.getTimestamp("pay_time")).thenReturn(null);
            when(rs.getTimestamp("cancel_time")).thenReturn(null);
            when(rs.getTimestamp("lock_expire_at")).thenReturn(Timestamp.valueOf(now.plusHours(1)));
            when(rs.getString("remark")).thenReturn(null);

            return List.of(rowMapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(contains("FROM customer_order"), any(RowMapper.class), eq(orderNo));
    }
}
