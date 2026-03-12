package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class UserPointsService {

    private static final BigDecimal DEFAULT_POINTS_PER_YUAN = BigDecimal.ONE;

    private final JdbcTemplate jdbcTemplate;

    public UserPointsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int awardPointsForAmount(Long userId, BigDecimal amount) {
        int points = calculatePoints(amount);
        if (userId == null || points <= 0) {
            return 0;
        }
        jdbcTemplate.update(
            "UPDATE user_customer SET points = COALESCE(points, 0) + ?, updated_at = NOW() WHERE id = ?",
            points,
            userId
        );
        return points;
    }

    public int rollbackPointsForAmount(Long userId, BigDecimal amount) {
        int points = calculatePoints(amount);
        if (userId == null || points <= 0) {
            return 0;
        }
        jdbcTemplate.update(
            "UPDATE user_customer SET points = GREATEST(COALESCE(points, 0) - ?, 0), updated_at = NOW() WHERE id = ?",
            points,
            userId
        );
        return points;
    }

    int calculatePoints(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return amount
            .multiply(resolvePointsPerYuan())
            .setScale(0, RoundingMode.DOWN)
            .intValue();
    }

    private BigDecimal resolvePointsPerYuan() {
        try {
            String configValue = jdbcTemplate.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = 'points_per_yuan'",
                String.class
            );
            if (configValue == null || configValue.isBlank()) {
                return DEFAULT_POINTS_PER_YUAN;
            }
            BigDecimal parsed = new BigDecimal(configValue.trim());
            return parsed.compareTo(BigDecimal.ZERO) > 0 ? parsed : DEFAULT_POINTS_PER_YUAN;
        } catch (Exception ignored) {
            return DEFAULT_POINTS_PER_YUAN;
        }
    }
}
