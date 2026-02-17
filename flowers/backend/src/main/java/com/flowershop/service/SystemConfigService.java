package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SystemConfigService {

    private final JdbcTemplate jdbcTemplate;

    public SystemConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listConfigs() {
        return jdbcTemplate.queryForList(
            """
            SELECT
                id,
                config_key AS configKey,
                config_value AS configValue,
                description,
                updated_at AS updatedAt
            FROM system_config
            ORDER BY id ASC
            """
        );
    }

    public void updateConfig(Long id, String configValue) {
        int updated = jdbcTemplate.update(
            "UPDATE system_config SET config_value = ?, updated_at = NOW() WHERE id = ?",
            configValue, id
        );
        if (updated == 0) {
            throw new BusinessException("CONFIG_NOT_FOUND", "配置项不存在: " + id);
        }
    }
}
