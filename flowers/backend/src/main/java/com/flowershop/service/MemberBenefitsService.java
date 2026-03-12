package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MemberBenefitsService {

    private static final int SPROUT_THRESHOLD = 300;
    private static final int CRAFT_THRESHOLD = 1000;
    private static final int MASTER_THRESHOLD = 2000;

    private static final BigDecimal NO_DISCOUNT_RATE = new BigDecimal("1.00");
    private static final BigDecimal SPROUT_DISCOUNT_RATE = new BigDecimal("0.98");
    private static final BigDecimal CRAFT_DISCOUNT_RATE = new BigDecimal("0.95");
    private static final BigDecimal MASTER_DISCOUNT_RATE = new BigDecimal("0.90");

    private final JdbcTemplate jdbcTemplate;

    public MemberBenefitsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrderBenefit resolveOrderBenefit(Long userId, BigDecimal goodsAmount) {
        Integer points = jdbcTemplate.queryForObject(
            "SELECT points FROM user_customer WHERE id = ?",
            Integer.class,
            userId
        );
        return resolveOrderBenefitByPoints(points, goodsAmount);
    }

    OrderBenefit resolveOrderBenefitByPoints(Integer pointsInput, BigDecimal goodsAmountInput) {
        int points = pointsInput == null ? 0 : Math.max(pointsInput, 0);
        BigDecimal goodsAmount = normalizeAmount(goodsAmountInput);
        BigDecimal discountRate = resolveDiscountRate(points);
        BigDecimal discountedGoodsAmount = goodsAmount
            .multiply(discountRate)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = goodsAmount
            .subtract(discountedGoodsAmount)
            .setScale(2, RoundingMode.HALF_UP);
        return new OrderBenefit(
            points,
            resolveLevelName(points),
            discountRate,
            discountAmount,
            discountedGoodsAmount
        );
    }

    private BigDecimal resolveDiscountRate(int points) {
        if (points >= MASTER_THRESHOLD) {
            return MASTER_DISCOUNT_RATE;
        }
        if (points >= CRAFT_THRESHOLD) {
            return CRAFT_DISCOUNT_RATE;
        }
        if (points >= SPROUT_THRESHOLD) {
            return SPROUT_DISCOUNT_RATE;
        }
        return NO_DISCOUNT_RATE;
    }

    private String resolveLevelName(int points) {
        if (points >= MASTER_THRESHOLD) {
            return "花语大师";
        }
        if (points >= CRAFT_THRESHOLD) {
            return "花匠会员";
        }
        if (points >= SPROUT_THRESHOLD) {
            return "新芽会员";
        }
        return "普通用户";
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public record OrderBenefit(
        int points,
        String levelName,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal discountedGoodsAmount
    ) {
    }
}
