package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void dashboardStatsFallBackToLegacyTablesWhenCurrentTablesAreEmpty() {
        StatsService statsService = new StatsService(jdbcTemplate);

        mockTable("user_customer", 1, 0);
        mockTable("users", 1, 8);
        mockTable("product", 1, 0);
        mockTable("products", 1, 6);
        mockTable("flower_material", 1, 0);
        mockTable("flowers", 1, 4);
        mockTable("customer_order", 1, 0);
        mockTable("orders", 1, 9);
        mockTable("inventory_batches", 1, 5);

        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM products WHERE status = 1", Integer.class))
            .thenReturn(6);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM flowers WHERE status = 1", Integer.class))
            .thenReturn(4);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM orders WHERE DATE(created_at) = CURDATE()", Integer.class))
            .thenReturn(2);
        when(jdbcTemplate.queryForObject(contains("SUM(COALESCE(pay_amount, total_amount))"), eq(BigDecimal.class)))
            .thenReturn(new BigDecimal("88.80"), new BigDecimal("188.80"), new BigDecimal("288.80"));
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)", Integer.class))
            .thenReturn(7);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)", Integer.class))
            .thenReturn(9);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM orders WHERE status = 'PAID'", Integer.class))
            .thenReturn(3);
        when(jdbcTemplate.queryForObject(contains("FROM flowers f"), eq(Integer.class)))
            .thenReturn(1);

        Map<String, Object> stats = statsService.getDashboardStats();

        assertEquals(8, stats.get("totalUsers"));
        assertEquals(6, stats.get("totalProducts"));
        assertEquals(4, stats.get("totalFlowers"));
        assertEquals(2, stats.get("todayOrders"));
        assertEquals(new BigDecimal("88.80"), stats.get("todaySales"));
        assertEquals(7, stats.get("weekOrders"));
        assertEquals(new BigDecimal("188.80"), stats.get("weekSales"));
        assertEquals(9, stats.get("monthOrders"));
        assertEquals(new BigDecimal("288.80"), stats.get("monthSales"));
        assertEquals(3, stats.get("pendingOrders"));
        assertEquals(1, stats.get("inventoryAlerts"));
    }

    private void mockTable(String tableName, int exists, int rowCount) {
        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq(tableName)))
            .thenReturn(exists);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableName, Integer.class))
            .thenReturn(rowCount);
    }
}
