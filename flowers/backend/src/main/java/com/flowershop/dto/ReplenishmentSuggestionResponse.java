package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReplenishmentSuggestionResponse(
    Long flowerId,
    String flowerName,
    LocalDate suggestionDate,
    BigDecimal predictedDemand,
    BigDecimal safetyStock,
    BigDecimal reorderPoint,
    BigDecimal onHand,
    BigDecimal inTransit,
    BigDecimal suggestedQty,
    String status,
    LocalDateTime generatedAt
) {
}
