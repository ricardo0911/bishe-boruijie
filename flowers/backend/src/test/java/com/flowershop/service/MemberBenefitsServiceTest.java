package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class MemberBenefitsServiceTest {

    private final MemberBenefitsService memberBenefitsService = new MemberBenefitsService(mock(JdbcTemplate.class));

    @Test
    void resolveOrderBenefitByPointsReturnsNoDiscountBelowThreshold() {
        MemberBenefitsService.OrderBenefit benefit = memberBenefitsService.resolveOrderBenefitByPoints(299, new BigDecimal("100.00"));

        assertEquals("普通用户", benefit.levelName());
        assertEquals(new BigDecimal("1.00"), benefit.discountRate());
        assertEquals(new BigDecimal("0.00"), benefit.discountAmount());
        assertEquals(new BigDecimal("100.00"), benefit.discountedGoodsAmount());
    }

    @Test
    void resolveOrderBenefitByPointsReturnsSproutDiscount() {
        MemberBenefitsService.OrderBenefit benefit = memberBenefitsService.resolveOrderBenefitByPoints(300, new BigDecimal("100.00"));

        assertEquals("新芽会员", benefit.levelName());
        assertEquals(new BigDecimal("0.98"), benefit.discountRate());
        assertEquals(new BigDecimal("2.00"), benefit.discountAmount());
        assertEquals(new BigDecimal("98.00"), benefit.discountedGoodsAmount());
    }

    @Test
    void resolveOrderBenefitByPointsReturnsCraftDiscount() {
        MemberBenefitsService.OrderBenefit benefit = memberBenefitsService.resolveOrderBenefitByPoints(1000, new BigDecimal("100.00"));

        assertEquals("花匠会员", benefit.levelName());
        assertEquals(new BigDecimal("0.95"), benefit.discountRate());
        assertEquals(new BigDecimal("5.00"), benefit.discountAmount());
        assertEquals(new BigDecimal("95.00"), benefit.discountedGoodsAmount());
    }

    @Test
    void resolveOrderBenefitByPointsReturnsMasterDiscount() {
        MemberBenefitsService.OrderBenefit benefit = memberBenefitsService.resolveOrderBenefitByPoints(2000, new BigDecimal("100.00"));

        assertEquals("花语大师", benefit.levelName());
        assertEquals(new BigDecimal("0.90"), benefit.discountRate());
        assertEquals(new BigDecimal("10.00"), benefit.discountAmount());
        assertEquals(new BigDecimal("90.00"), benefit.discountedGoodsAmount());
    }
}
