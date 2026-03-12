package com.flowershop.service;

import com.flowershop.dto.RecommendationResponse;
import com.flowershop.dto.ReplenishmentSuggestionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnalysisQueryService {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReplenishmentSuggestionResponse> listReplenishmentSuggestions(LocalDate date) {
        LocalDate targetDate = date == null ? LocalDate.now() : date;
        List<ReplenishmentSuggestionResponse> suggestions = listStoredReplenishmentSuggestions(targetDate);
        if (!suggestions.isEmpty()) {
            return suggestions;
        }
        return listRealtimeReplenishmentSuggestions(targetDate);
    }

    private List<ReplenishmentSuggestionResponse> listStoredReplenishmentSuggestions(LocalDate targetDate) {
        if (!tableExists("replenishment_suggestion")) {
            return List.of();
        }
        return jdbcTemplate.query(
            """
            SELECT
                rs.flower_id,
                f.name AS flower_name,
                rs.suggestion_date,
                rs.predicted_demand,
                rs.safety_stock,
                rs.reorder_point,
                rs.on_hand,
                rs.in_transit,
                rs.suggested_qty,
                rs.status,
                rs.generated_at
            FROM replenishment_suggestion rs
            JOIN flower_material f ON rs.flower_id = f.id
            WHERE rs.suggestion_date = ?
            ORDER BY rs.suggested_qty DESC, rs.flower_id
            """,
            (rs, rowNum) -> new ReplenishmentSuggestionResponse(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getDate("suggestion_date").toLocalDate(),
                rs.getBigDecimal("predicted_demand"),
                rs.getBigDecimal("safety_stock"),
                rs.getBigDecimal("reorder_point"),
                rs.getBigDecimal("on_hand"),
                rs.getBigDecimal("in_transit"),
                rs.getBigDecimal("suggested_qty"),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("generated_at"))
            ),
            targetDate
        );
    }

    private List<ReplenishmentSuggestionResponse> listRealtimeReplenishmentSuggestions(LocalDate targetDate) {
        return jdbcTemplate.query(
            """
            SELECT
                f.id AS flower_id,
                f.name AS flower_name,
                ? AS suggestion_date,
                ROUND(COALESCE(NULLIF(recent_usage.predicted_demand, 0), f.warn_threshold), 2) AS predicted_demand,
                ROUND(f.warn_threshold, 2) AS safety_stock,
                ROUND(GREATEST(COALESCE(recent_usage.predicted_demand, 0), f.warn_threshold) + f.warn_threshold, 2) AS reorder_point,
                ROUND(COALESCE(stock.on_hand, 0), 2) AS on_hand,
                0 AS in_transit,
                ROUND(
                    GREATEST(
                        (GREATEST(COALESCE(recent_usage.predicted_demand, 0), f.warn_threshold) + f.warn_threshold) - COALESCE(stock.on_hand, 0),
                        0
                    ),
                    2
                ) AS suggested_qty,
                CASE
                    WHEN COALESCE(stock.on_hand, 0) <= 0 THEN 'URGENT'
                    WHEN COALESCE(stock.on_hand, 0) <= f.warn_threshold THEN 'NEW'
                    ELSE 'NORMAL'
                END AS status,
                NOW() AS generated_at
            FROM flower_material f
            LEFT JOIN (
                SELECT
                    ib.flower_id,
                    COALESCE(SUM(ib.current_qty - ib.locked_qty), 0) AS on_hand
                FROM inventory_batch ib
                GROUP BY ib.flower_id
            ) stock ON stock.flower_id = f.id
            LEFT JOIN (
                SELECT
                    b.flower_id,
                    COALESCE(SUM(oi.quantity * b.dosage), 0) AS predicted_demand
                FROM product_bom b
                JOIN order_item oi ON oi.product_id = b.product_id
                JOIN customer_order o ON o.id = oi.order_id
                WHERE o.status IN ('PAID', 'CONFIRMED', 'COMPLETED')
                  AND COALESCE(o.pay_time, o.created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
                GROUP BY b.flower_id
            ) recent_usage ON recent_usage.flower_id = f.id
            WHERE f.enabled = 1
            ORDER BY suggested_qty DESC, f.id
            """,
            (rs, rowNum) -> new ReplenishmentSuggestionResponse(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getDate("suggestion_date").toLocalDate(),
                defaultZero(rs.getBigDecimal("predicted_demand")),
                defaultZero(rs.getBigDecimal("safety_stock")),
                defaultZero(rs.getBigDecimal("reorder_point")),
                defaultZero(rs.getBigDecimal("on_hand")),
                defaultZero(rs.getBigDecimal("in_transit")),
                defaultZero(rs.getBigDecimal("suggested_qty")),
                rs.getString("status"),
                toLocalDateTime(rs.getTimestamp("generated_at"))
            ),
            java.sql.Date.valueOf(targetDate)
        );
    }

    public List<RecommendationResponse> listRecommendations(Long userId, Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(50, limit));
        String sql = """
            SELECT rr.user_id, rr.product_id, p.title AS product_title, rr.score, rr.reason, rr.generated_at
            FROM recommendation_result rr
            JOIN product p ON rr.product_id = p.id
            WHERE rr.user_id = ?
            ORDER BY rr.score DESC, rr.generated_at DESC
            LIMIT %d
            """.formatted(safeLimit);

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new RecommendationResponse(
                rs.getLong("user_id"),
                rs.getLong("product_id"),
                rs.getString("product_title"),
                rs.getBigDecimal("score"),
                rs.getString("reason"),
                toLocalDateTime(rs.getTimestamp("generated_at"))
            ),
            userId
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }
}
