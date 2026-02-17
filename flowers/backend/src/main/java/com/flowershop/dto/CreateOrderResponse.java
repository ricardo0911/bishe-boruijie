package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateOrderResponse(
    String orderNo,
    String status,
    BigDecimal totalAmount,
    LocalDateTime lockExpireAt
) {
}
