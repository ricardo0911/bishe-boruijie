package com.flowershop.dto;

public record ConfirmOrderRequest(
    String logisticsCompany,
    String trackingNo
) {
}
