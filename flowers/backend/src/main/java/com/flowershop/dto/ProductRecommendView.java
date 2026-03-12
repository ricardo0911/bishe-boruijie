package com.flowershop.dto;

import java.math.BigDecimal;

public record ProductRecommendView(
    Long id,
    String title,
    String type,
    String category,
    BigDecimal unitPrice,
    String coverImage,
    String status,
    Integer recentSales,
    String merchantAccount,
    String merchantName
) {
}
