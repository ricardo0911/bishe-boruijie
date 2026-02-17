package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RecommendationResponse(
    Long userId,
    Long productId,
    String productTitle,
    BigDecimal score,
    String reason,
    LocalDateTime generatedAt
) {
}
