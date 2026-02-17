package com.flowershop.dto;

import java.math.BigDecimal;

public record ProductView(
    Long id,
    String title,
    String type,
    String category,
    BigDecimal unitPrice,
    String coverImage,
    String status
) {
}
