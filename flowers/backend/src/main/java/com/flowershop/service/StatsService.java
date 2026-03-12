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

    // ===== 闂佸簱鍋撻柛鐑嗗枟椤┭呮喆閸垻鍩犻悹?=====

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

    // ===== 闂佸簱鍋撻柛鐑嗗枦缁夊ジ宕濋崹顔肩€婚柡?=====

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

    // ===== 闁绘埈鍙冮弨銏ゅ疮閸℃鎯傞柟鐑樺笩椤?=====

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

    // ===== 闁告繀鑳剁悮顐︽煥閳ь剟宕鐓庣€婚柡?=====

    public CategorySalesResponse getCategorySales(LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        // 闁兼儳鍢茶ぐ鍥箑婵犳碍鏁橀柛鐑嗗櫍椤ゅ倿鎮介妸銈囪壘閻犱緤绱曢悾濠氬础閻樺磭妲?
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

    // ===== 濞达絽楠哥花杈┾偓娑欙耿椤ｂ晝鎷?=====

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

    // ===== 闁告鍠愬﹢浣圭椤忓洢鈧啴寮剁捄銊у煚閻犱讲妲勭槐娆愮┍濠靛牊娈岄柨?=====

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        String userTable = resolvePreferredTable("user_customer", "users");
        stats.put("totalUsers", switch (userTable) {
            case "users" -> queryInt("SELECT COUNT(1) FROM users");
            case "user_customer" -> queryInt("SELECT COUNT(1) FROM user_customer");
            default -> 0;
        });

        String productTable = resolvePreferredTable("product", "products");
        stats.put("totalProducts", switch (productTable) {
            case "products" -> queryInt("SELECT COUNT(1) FROM products WHERE status = 1");
            case "product" -> queryInt("SELECT COUNT(1) FROM product WHERE status = 'ON_SALE'");
            default -> 0;
        });

        String flowerTable = resolvePreferredTable("flower_material", "flowers");
        stats.put("totalFlowers", switch (flowerTable) {
            case "flowers" -> queryInt("SELECT COUNT(1) FROM flowers WHERE status = 1");
            case "flower_material" -> queryInt("SELECT COUNT(1) FROM flower_material WHERE enabled = 1");
            default -> 0;
        });

        String orderTable = resolvePreferredTable("customer_order", "orders");
        if ("orders".equals(orderTable)) {
            stats.put("todayOrders", queryInt("SELECT COUNT(1) FROM orders WHERE DATE(created_at) = CURDATE()"));
            stats.put("todaySales", queryDecimal("""
                SELECT COALESCE(SUM(COALESCE(pay_amount, total_amount)), 0)
                FROM orders
                WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','PROCESSING','DELIVERING','COMPLETED')
                """));
            stats.put("weekOrders", queryInt("SELECT COUNT(1) FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)"));
            stats.put("weekSales", queryDecimal("""
                SELECT COALESCE(SUM(COALESCE(pay_amount, total_amount)), 0)
                FROM orders
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                  AND status IN ('PAID','PROCESSING','DELIVERING','COMPLETED')
                """));
            stats.put("monthOrders", queryInt("SELECT COUNT(1) FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)"));
            stats.put("monthSales", queryDecimal("""
                SELECT COALESCE(SUM(COALESCE(pay_amount, total_amount)), 0)
                FROM orders
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                  AND status IN ('PAID','PROCESSING','DELIVERING','COMPLETED')
                """));
            stats.put("pendingOrders", queryInt("SELECT COUNT(1) FROM orders WHERE status = 'PAID'"));
        } else if ("customer_order".equals(orderTable)) {
            stats.put("todayOrders", queryInt("SELECT COUNT(1) FROM customer_order WHERE DATE(created_at) = CURDATE()"));
            stats.put("todaySales", queryDecimal("""
                SELECT COALESCE(SUM(payment_amount), 0)
                FROM customer_order
                WHERE DATE(created_at) = CURDATE() AND status IN ('PAID','CONFIRMED','COMPLETED')
                """));
            stats.put("weekOrders", queryInt("SELECT COUNT(1) FROM customer_order WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)"));
            stats.put("weekSales", queryDecimal("""
                SELECT COALESCE(SUM(payment_amount), 0)
                FROM customer_order
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                  AND status IN ('PAID','CONFIRMED','COMPLETED')
                """));
            stats.put("monthOrders", queryInt("SELECT COUNT(1) FROM customer_order WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)"));
            stats.put("monthSales", queryDecimal("""
                SELECT COALESCE(SUM(payment_amount), 0)
                FROM customer_order
                WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
                  AND status IN ('PAID','CONFIRMED','COMPLETED')
                """));
            stats.put("pendingOrders", queryInt("SELECT COUNT(1) FROM customer_order WHERE status = 'PAID'"));
        } else {
            stats.put("todayOrders", 0);
            stats.put("todaySales", BigDecimal.ZERO);
            stats.put("weekOrders", 0);
            stats.put("weekSales", BigDecimal.ZERO);
            stats.put("monthOrders", 0);
            stats.put("monthSales", BigDecimal.ZERO);
            stats.put("pendingOrders", 0);
        }

        if ("flowers".equals(flowerTable) && tableExists("inventory_batches")) {
            stats.put("inventoryAlerts", queryInt("""
                SELECT COUNT(1)
                FROM flowers f
                WHERE f.status = 1 AND f.alert_threshold >
                    (SELECT COALESCE(SUM(ib.current_qty), 0) FROM inventory_batches ib WHERE ib.flower_id = f.id)
                """));
        } else if ("flower_material".equals(flowerTable) && tableExists("inventory_batch")) {
            stats.put("inventoryAlerts", queryInt("""
                SELECT COUNT(1)
                FROM flower_material f
                WHERE f.enabled = 1 AND f.warn_threshold >
                    (SELECT COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) FROM inventory_batch ib WHERE ib.flower_id = f.id)
                """));
        } else {
            stats.put("inventoryAlerts", 0);
        }

        return stats;
    }

    private int queryInt(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value != null ? value : 0;
    }

    private BigDecimal queryDecimal(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value != null ? value : BigDecimal.ZERO;
    }

    private String resolvePreferredTable(String currentTable, String legacyTable) {
        if (tableHasRows(currentTable)) {
            return currentTable;
        }
        if (tableHasRows(legacyTable)) {
            return legacyTable;
        }
        if (tableExists(currentTable)) {
            return currentTable;
        }
        if (tableExists(legacyTable)) {
            return legacyTable;
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private boolean tableHasRows(String tableName) {
        if (!tableExists(tableName)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM " + tableName, Integer.class);
        return count != null && count > 0;
    }
}