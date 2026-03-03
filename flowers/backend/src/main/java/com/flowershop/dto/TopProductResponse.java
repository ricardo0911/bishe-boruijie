package com.flowershop.dto;

import java.math.BigDecimal;
import java.util.List;

public record TopProductResponse(
    Integer limit,
    List<TopProductItem> products
) {
    public record TopProductItem(
        Long productId,
        String productTitle,
        String category,
        Integer totalQuantity,
        BigDecimal totalSales,
        Integer orderCount
    ) {
    }
}
