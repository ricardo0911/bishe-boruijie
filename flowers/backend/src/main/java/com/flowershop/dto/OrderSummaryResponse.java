package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(
    String orderNo,
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
    String remark,
    List<OrderItemResponse> items
) {
}
