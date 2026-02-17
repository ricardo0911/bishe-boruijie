package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class FlowerMaterialService {

    private final JdbcTemplate jdbcTemplate;

    public FlowerMaterialService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listAll(String category) {
        if (category != null && !category.isBlank()) {
            return jdbcTemplate.queryForList(
                "SELECT * FROM flower_material WHERE category = ? ORDER BY id DESC",
                category.trim()
            );
        }
        return jdbcTemplate.queryForList("SELECT * FROM flower_material ORDER BY id DESC");
    }

    public Map<String, Object> getById(Long id) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(
            "SELECT * FROM flower_material WHERE id = ?", id
        );
        if (list.isEmpty()) {
            throw new BusinessException("FLOWER_NOT_FOUND", "花材不存在: " + id);
        }
        return list.get(0);
    }

    public void create(String name, String category, String unit, BigDecimal salePrice,
                       BigDecimal costPrice, int shelfLifeDays, BigDecimal warnThreshold, String image) {
        jdbcTemplate.update(
            """
            INSERT INTO flower_material(name, category, unit, sale_price, cost_price, shelf_life_days, warn_threshold, image_url, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            name, category, unit, salePrice, costPrice, shelfLifeDays, warnThreshold, image
        );
    }

    public void update(Long id, String name, String category, String unit, BigDecimal salePrice,
                       BigDecimal costPrice, int shelfLifeDays, BigDecimal warnThreshold, boolean enabled, String image) {
        int updated = jdbcTemplate.update(
            """
            UPDATE flower_material
            SET name = ?, category = ?, unit = ?, sale_price = ?, cost_price = ?,
                shelf_life_days = ?, warn_threshold = ?, enabled = ?, image_url = ?, updated_at = NOW()
            WHERE id = ?
            """,
            name, category, unit, salePrice, costPrice, shelfLifeDays, warnThreshold, enabled ? 1 : 0, image, id
        );
        if (updated == 0) {
            throw new BusinessException("FLOWER_NOT_FOUND", "花材不存在: " + id);
        }
    }
}
