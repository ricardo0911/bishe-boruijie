package com.flowershop.service;

import com.flowershop.dto.CategorySalesResponse;
import com.flowershop.dto.LowStockAlertResponse;
import com.flowershop.dto.SalesOverviewResponse;
import com.flowershop.dto.SalesTrendResponse;
import com.flowershop.dto.TopProductResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final JdbcTemplate jdbcTemplate;

    public StatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===== 销售概览统计 =====

    public SalesOverviewResponse getSalesOverview() {
        return new SalesOverviewResponse(
            getTodayStats(),
            getWeekStats(),
            getMonthStats()
        );
    }

    private SalesOverviewResponse.TodayStats getTodayStats() {
        String sql = """
            SELECT
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE DATE(created_at) = CURDATE()
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new SalesOverviewResponse.TodayStats(
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ));
    }

    private SalesOverviewResponse.WeekStats getWeekStats() {
        String sql = """
            SELECT
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new SalesOverviewResponse.WeekStats(
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ));
    }

    private SalesOverviewResponse.MonthStats getMonthStats() {
        String sql = """
            SELECT
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new SalesOverviewResponse.MonthStats(
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ));
    }

    // ===== 销售趋势分析 =====

    public SalesTrendResponse getSalesTrend(String period, LocalDate startDate, LocalDate endDate) {
        String periodType = validatePeriod(period);
        LocalDate effectiveStart = startDate != null ? startDate : getDefaultStartDate(periodType);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        List<SalesTrendResponse.TrendItem> data = switch (periodType) {
            case "daily" -> getDailyTrend(effectiveStart, effectiveEnd);
            case "weekly" -> getWeeklyTrend(effectiveStart, effectiveEnd);
            case "monthly" -> getMonthlyTrend(effectiveStart, effectiveEnd);
            default -> getDailyTrend(effectiveStart, effectiveEnd);
        };

        return new SalesTrendResponse(periodType, data);
    }

    private String validatePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "daily";
        }
        return switch (period.toLowerCase()) {
            case "weekly", "week" -> "weekly";
            case "monthly", "month" -> "monthly";
            default -> "daily";
        };
    }

    private LocalDate getDefaultStartDate(String periodType) {
        return switch (periodType) {
            case "daily" -> LocalDate.now().minusDays(30);
            case "weekly" -> LocalDate.now().minusWeeks(12);
            case "monthly" -> LocalDate.now().minusMonths(12);
            default -> LocalDate.now().minusDays(30);
        };
    }

    private List<SalesTrendResponse.TrendItem> getDailyTrend(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                DATE(created_at) AS date,
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE DATE(created_at) BETWEEN ? AND ?
            GROUP BY DATE(created_at)
            ORDER BY date
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SalesTrendResponse.TrendItem(
            rs.getDate("date").toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ), Date.valueOf(startDate), Date.valueOf(endDate));
    }

    private List<SalesTrendResponse.TrendItem> getWeeklyTrend(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                CONCAT(YEAR(created_at), '-W', LPAD(WEEK(created_at, 1), 2, '0')) AS weekLabel,
                YEAR(created_at) AS year,
                WEEK(created_at, 1) AS week,
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE DATE(created_at) BETWEEN ? AND ?
            GROUP BY YEAR(created_at), WEEK(created_at, 1)
            ORDER BY year, week
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SalesTrendResponse.TrendItem(
            rs.getString("weekLabel"),
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ), Date.valueOf(startDate), Date.valueOf(endDate));
    }

    private List<SalesTrendResponse.TrendItem> getMonthlyTrend(LocalDate startDate, LocalDate endDate) {
        String sql = """
            SELECT
                DATE_FORMAT(created_at, '%Y-%m') AS monthLabel,
                YEAR(created_at) AS year,
                MONTH(created_at) AS month,
                COUNT(1) AS orderCount,
                COALESCE(SUM(total_amount), 0) AS salesAmount,
                COUNT(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN 1 END) AS paidOrderCount,
                COALESCE(SUM(CASE WHEN status IN ('PAID','CONFIRMED','COMPLETED') THEN payment_amount END), 0) AS paidAmount
            FROM customer_order
            WHERE DATE(created_at) BETWEEN ? AND ?
            GROUP BY YEAR(created_at), MONTH(created_at)
            ORDER BY year, month
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SalesTrendResponse.TrendItem(
            rs.getString("monthLabel"),
            rs.getInt("orderCount"),
            rs.getBigDecimal("salesAmount"),
            rs.getInt("paidOrderCount"),
            rs.getBigDecimal("paidAmount")
        ), Date.valueOf(startDate), Date.valueOf(endDate));
    }

    // ===== 热销商品排行 =====

    public TopProductResponse getTopProducts(Integer limit, LocalDate startDate, LocalDate endDate) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 100));
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        String sql = """
            SELECT
                p.id AS productId,
                p.title AS productTitle,
                p.category,
                COALESCE(SUM(oi.quantity), 0) AS totalQuantity,
                COALESCE(SUM(oi.line_amount), 0) AS totalSales,
                COUNT(DISTINCT oi.order_id) AS orderCount
            FROM order_item oi
            JOIN customer_order o ON oi.order_id = o.id
            JOIN product p ON oi.product_id = p.id
            WHERE o.status IN ('PAID','CONFIRMED','COMPLETED')
              AND DATE(o.pay_time) BETWEEN ? AND ?
            GROUP BY p.id, p.title, p.category
            ORDER BY totalQuantity DESC, totalSales DESC
            LIMIT ?
            """;

        List<TopProductResponse.TopProductItem> products = jdbcTemplate.query(sql,
            (rs, rowNum) -> new TopProductResponse.TopProductItem(
                rs.getLong("productId"),
                rs.getString("productTitle"),
                rs.getString("category"),
                rs.getInt("totalQuantity"),
                rs.getBigDecimal("totalSales"),
                rs.getInt("orderCount")
            ),
            Date.valueOf(effectiveStart), Date.valueOf(effectiveEnd), safeLimit
        );

        return new TopProductResponse(safeLimit, products);
    }

    // ===== 品类销售分析 =====

    public CategorySalesResponse getCategorySales(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        // 获取总销售额用于计算占比
        BigDecimal totalSales = jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(oi.line_amount), 0)
            FROM order_item oi
            JOIN customer_order o ON oi.order_id = o.id
            WHERE o.status IN ('PAID','CONFIRMED','COMPLETED')
              AND DATE(o.pay_time) BETWEEN ? AND ?
            """, BigDecimal.class, Date.valueOf(effectiveStart), Date.valueOf(effectiveEnd));

        Integer totalOrderCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(DISTINCT o.id)
            FROM customer_order o
            WHERE o.status IN ('PAID','CONFIRMED','COMPLETED')
              AND DATE(o.pay_time) BETWEEN ? AND ?
            """, Integer.class, Date.valueOf(effectiveStart), Date.valueOf(effectiveEnd));

        String sql = """
            SELECT
                p.category,
                COUNT(DISTINCT p.id) AS productCount,
                COUNT(DISTINCT o.id) AS orderCount,
                COALESCE(SUM(oi.quantity), 0) AS quantitySold,
                COALESCE(SUM(oi.line_amount), 0) AS salesAmount
            FROM order_item oi
            JOIN customer_order o ON oi.order_id = o.id
            JOIN product p ON oi.product_id = p.id
            WHERE o.status IN ('PAID','CONFIRMED','COMPLETED')
              AND DATE(o.pay_time) BETWEEN ? AND ?
            GROUP BY p.category
            ORDER BY salesAmount DESC
            """;

        BigDecimal finalTotalSales = totalSales != null ? totalSales : BigDecimal.ZERO;
        List<CategorySalesResponse.CategoryItem> categories = jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                BigDecimal salesAmount = rs.getBigDecimal("salesAmount");
                double percentage = finalTotalSales.compareTo(BigDecimal.ZERO) > 0
                    ? salesAmount.multiply(BigDecimal.valueOf(100)).divide(finalTotalSales, 2, BigDecimal.ROUND_HALF_UP).doubleValue()
                    : 0.0;
                return new CategorySalesResponse.CategoryItem(
                    rs.getString("category"),
                    rs.getInt("productCount"),
                    rs.getInt("orderCount"),
                    rs.getInt("quantitySold"),
                    salesAmount,
                    percentage
                );
            },
            Date.valueOf(effectiveStart), Date.valueOf(effectiveEnd)
        );

        return new CategorySalesResponse(
            categories,
            finalTotalSales,
            totalOrderCount != null ? totalOrderCount : 0
        );
    }

    // ===== 低库存预警 =====

    public LowStockAlertResponse getLowStockAlerts(Integer threshold) {
        String sql = """
            SELECT
                f.id AS flowerId,
                f.name AS flowerName,
                f.unit,
                COALESCE(SUM(ib.current_qty), 0) AS currentStock,
                COALESCE(SUM(ib.locked_qty), 0) AS lockedStock,
                COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) AS availableStock,
                f.warn_threshold AS warnThreshold,
                CASE
                    WHEN COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) <= 0 THEN 'CRITICAL'
                    WHEN COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) <= f.warn_threshold * 0.5 THEN 'HIGH'
                    WHEN COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) <= f.warn_threshold THEN 'MEDIUM'
                    ELSE 'LOW'
                END AS alertLevel
            FROM flower_material f
            LEFT JOIN inventory_batch ib ON f.id = ib.flower_id
            WHERE f.enabled = 1
            GROUP BY f.id, f.name, f.unit, f.warn_threshold
            HAVING availableStock <= COALESCE(?, f.warn_threshold)
            ORDER BY availableStock ASC, f.id
            """;

        List<LowStockAlertResponse.LowStockItem> items = jdbcTemplate.query(sql,
            (rs, rowNum) -> new LowStockAlertResponse.LowStockItem(
                rs.getLong("flowerId"),
                rs.getString("flowerName"),
                rs.getString("unit"),
                rs.getBigDecimal("currentStock"),
                rs.getBigDecimal("lockedStock"),
                rs.getBigDecimal("availableStock"),
                rs.getInt("warnThreshold"),
                rs.getString("alertLevel")
            ),
            threshold
        );

        return new LowStockAlertResponse(items.size(), items);
    }

    // ===== 原有仪表板统计（保留） =====

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
