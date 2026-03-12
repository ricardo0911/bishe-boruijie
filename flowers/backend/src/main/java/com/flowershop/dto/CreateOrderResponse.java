package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderResponse(
    String orderNo,
    String status,
    BigDecimal totalAmount,
    LocalDateTime lockExpireAt,
    List<SubOrder> orders
) {
    public record SubOrder(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        LocalDateTime lockExpireAt,
        String merchantAccount,
        String merchantName
    ) {
    }
}
