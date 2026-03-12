package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CategoryService {

    private final JdbcTemplate jdbcTemplate;

    public CategoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listEnabled() {
        List<Map<String, Object>> categories = new ArrayList<>(jdbcTemplate.queryForList(
            """
            SELECT id, code, name, sort_order AS sortOrder, icon, enabled
            FROM category
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """
        ));

        Set<String> existingKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : categories) {
            existingKeys.add(normalizeKey(row.get("code")));
            existingKeys.add(normalizeKey(row.get("name")));
        }

        List<Map<String, Object>> dynamicCategories = jdbcTemplate.queryForList(
            """
            SELECT DISTINCT TRIM(category) AS category_name
            FROM product
            WHERE status = 'ON_SALE'
              AND category IS NOT NULL
              AND TRIM(category) <> ''
            ORDER BY category_name ASC
            """
        );

        int sortBase = categories.size() + 100;
        int dynamicIndex = 0;
        for (Map<String, Object> row : dynamicCategories) {
            String categoryName = stringValue(row.get("category_name"));
            if (categoryName.isBlank()) {
                continue;
            }

            String normalized = normalizeKey(categoryName);
            if (existingKeys.contains(normalized)) {
                continue;
            }

            Map<String, Object> dynamic = new LinkedHashMap<>();
            dynamic.put("id", "custom:" + categoryName);
            dynamic.put("code", categoryName);
            dynamic.put("name", categoryName);
            dynamic.put("sortOrder", sortBase + dynamicIndex);
            dynamic.put("icon", "");
            dynamic.put("enabled", 1);
            dynamic.put("custom", true);
            categories.add(dynamic);

            existingKeys.add(normalized);
            dynamicIndex++;
        }

        return categories;
    }

    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList(
            """
            SELECT id, code, name, sort_order AS sortOrder, icon, enabled,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM category
            ORDER BY sort_order ASC, id ASC
            """
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String normalizeKey(Object value) {
        return stringValue(value).toUpperCase(Locale.ROOT);
    }
}