package com.flowershop.dto;

import java.math.BigDecimal;
import java.util.List;

public record LowStockAlertResponse(
    Integer alertCount,
    List<LowStockItem> items
) {
    public record LowStockItem(
        Long flowerId,
        String flowerName,
        String unit,
        BigDecimal currentStock,
        BigDecimal lockedStock,
        BigDecimal availableStock,
        Integer warnThreshold,
        String alertLevel
    ) {
    }
}
