package com.flowershop.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailView(
    Long id,
    String title,
    String type,
    String category,
    String description,
    String coverImage,
    BigDecimal basePrice,
    BigDecimal packagingFee,
    BigDecimal deliveryFee,
    BigDecimal unitPrice,
    BigDecimal autoPrice,
    String status,
    List<BomItemView> bomItems
) {
}
