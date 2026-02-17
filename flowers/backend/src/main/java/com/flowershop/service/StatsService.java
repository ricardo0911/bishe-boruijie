package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatsService {

    private final JdbcTemplate jdbcTemplate;

    public StatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 用户数
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM user_customer", Integer.class);
        stats.put("totalUsers", userCount != null ? userCount : 0);

        // 商品数
        Integer productCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM product WHERE status = 'ON_SALE'", Integer.class);
        stats.put("totalProducts", productCount != null ? productCount : 0);

        // 花材种类
        Integer flowerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM flower_material WHERE enabled = 1", Integer.class);
        stats.put("totalFlowers", flowerCount != null ? flowerCount : 0);

        // 今日订单数
        Integer todayOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE DATE(created_at) = CURDATE()", Integer.class);
        stats.put("todayOrders", todayOrders != null ? todayOrders : 0);

        // 今日销售额
        BigDecimal todaySales = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(payment_amount), 0)
            FROM customer_order
            WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','CONFIRMED','COMPLETED')
            """, BigDecimal.class);
        stats.put("todaySales", todaySales != null ? todaySales : BigDecimal.ZERO);

        // 近7天订单数
        Integer weekOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)", Integer.class);
        stats.put("weekOrders", weekOrders != null ? weekOrders : 0);

        // 近7天销售额
        BigDecimal weekSales = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(payment_amount), 0)
            FROM customer_order
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
              AND status IN ('PAID','CONFIRMED','COMPLETED')
            """, BigDecimal.class);
        stats.put("weekSales", weekSales != null ? weekSales : BigDecimal.ZERO);

        // 近30天订单数
        Integer monthOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)", Integer.class);
        stats.put("monthOrders", monthOrders != null ? monthOrders : 0);

        // 近30天销售额
        BigDecimal monthSales = jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(payment_amount), 0)
            FROM customer_order
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
              AND status IN ('PAID','CONFIRMED','COMPLETED')
            """, BigDecimal.class);
        stats.put("monthSales", monthSales != null ? monthSales : BigDecimal.ZERO);

        // 待处理订单（PAID 但还未 CONFIRMED/COMPLETED）
        Integer pendingOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM customer_order WHERE status = 'PAID'", Integer.class);
        stats.put("pendingOrders", pendingOrders != null ? pendingOrders : 0);

        // 库存预警数
        Integer alertCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM flower_material f
            WHERE f.enabled = 1 AND f.warn_threshold >
                (SELECT COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) FROM inventory_batch ib WHERE ib.flower_id = f.id)
            """, Integer.class);
        stats.put("inventoryAlerts", alertCount != null ? alertCount : 0);

        return stats;
    }
}
