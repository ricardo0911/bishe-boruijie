package com.flowershop.dto;

import java.math.BigDecimal;

public record BomItemView(
    Long flowerId,
    String flowerName,
    BigDecimal dosage,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
}
