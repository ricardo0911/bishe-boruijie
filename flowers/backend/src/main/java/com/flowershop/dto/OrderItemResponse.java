package com.flowershop.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Long productId,
    String productTitle,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineAmount
) {
}
