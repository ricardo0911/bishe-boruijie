package com.flowershop.service;

import com.flowershop.dto.RecommendationResponse;
import com.flowershop.dto.ReplenishmentSuggestionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
}
