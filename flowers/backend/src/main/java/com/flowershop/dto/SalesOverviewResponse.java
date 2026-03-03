package com.flowershop.dto;

import java.math.BigDecimal;

public record SalesOverviewResponse(
    TodayStats today,
    WeekStats week,
    MonthStats month
) {
    public record TodayStats(
        Integer orderCount,
        BigDecimal salesAmount,
        Integer paidOrderCount,
        BigDecimal paidAmount
    ) {
    }

    public record WeekStats(
        Integer orderCount,
        BigDecimal salesAmount,
        Integer paidOrderCount,
        BigDecimal paidAmount
    ) {
    }

    public record MonthStats(
        Integer orderCount,
        BigDecimal salesAmount,
        Integer paidOrderCount,
        BigDecimal paidAmount
    ) {
    }
}
