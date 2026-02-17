package com.flowershop.dto;

public record SimpleActionResponse(
    String orderNo,
    String status,
    String message
) {
}
