package com.flowershop.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesTrendResponse(
    String period,
    List<TrendItem> data
) {
    public record TrendItem(
        String date,
        Integer orderCount,
        BigDecimal salesAmount,
        Integer paidOrderCount,
        BigDecimal paidAmount
    ) {
    }
}
