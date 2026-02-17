package com.flowershop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BatchStockResponse(
    Long batchId,
    Long flowerId,
    String qualityStatus,
    LocalDateTime receiptTime,
    LocalDateTime wiltTime,
    BigDecimal currentQty,
    BigDecimal lockedQty,
    BigDecimal availableQty,
    String supplierName
) {
}
