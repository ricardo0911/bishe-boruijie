package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(
    String orderNo,
    String status,
    BigDecimal totalAmount,
    BigDecimal paymentAmount,
    BigDecimal deliveryFee,
    String receiverName,
    String receiverPhone,
    String receiverAddress,
    String deliveryMode,
    String deliverySlot,
    LocalDateTime createdAt,
    LocalDateTime payTime,
    LocalDateTime cancelTime,
    LocalDateTime lockExpireAt,
    String trackingCompany,
    String trackingNo,
    LocalDateTime shippedAt,
    String remark,
    List<OrderItemResponse> items
) {
}
