package com.flowershop.service;

public record AuthAccount(
    String loginType,
    String account,
    String passwordDigest,
    String displayName,
    String roleCode,
    boolean enabled
) {
}