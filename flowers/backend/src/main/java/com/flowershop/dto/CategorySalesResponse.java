package com.flowershop.dto;

import java.math.BigDecimal;
import java.util.List;

public record CategorySalesResponse(
    List<CategoryItem> categories,
    BigDecimal totalSales,
    Integer totalOrderCount
) {
    public record CategoryItem(
        String category,
        Integer productCount,
        Integer orderCount,
        Integer quantitySold,
        BigDecimal salesAmount,
        Double percentage
    ) {
    }
}
