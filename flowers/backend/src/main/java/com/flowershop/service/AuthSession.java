package com.flowershop.service;

public record AuthSession(
    String token,
    String loginType,
    String account,
    String roleCode
) {
}