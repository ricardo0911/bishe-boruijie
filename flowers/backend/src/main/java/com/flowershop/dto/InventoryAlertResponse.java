package com.flowershop.dto;

import java.math.BigDecimal;

public record InventoryAlertResponse(
    Long flowerId,
    String flowerName,
    BigDecimal availableQty,
    BigDecimal warnThreshold,
    String warningLevel
) {
}
