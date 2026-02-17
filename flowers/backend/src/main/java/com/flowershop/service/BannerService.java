package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class BannerService {

    private final JdbcTemplate jdbcTemplate;

    public BannerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listEnabled() {
        return jdbcTemplate.queryForList(
            """
            SELECT id, title, subtitle, color_from AS colorFrom, color_to AS colorTo,
                   link_url AS linkUrl, sort_order AS sortOrder
            FROM banner
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """
        );
    }
}
