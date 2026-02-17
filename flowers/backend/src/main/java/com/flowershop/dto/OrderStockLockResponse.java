package com.flowershop.dto;

import java.math.BigDecimal;

public record OrderStockLockResponse(
    Long flowerId,
    String flowerName,
    BigDecimal lockQty,
    String status
) {
}
