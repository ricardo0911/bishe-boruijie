package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

    private final JdbcTemplate jdbcTemplate;

    public CategoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listEnabled() {
        return jdbcTemplate.queryForList(
            """
            SELECT id, code, name, sort_order AS sortOrder, icon, enabled
            FROM category
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """
        );
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
}
