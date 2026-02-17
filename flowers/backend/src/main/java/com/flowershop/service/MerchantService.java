package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MerchantService {

    private final JdbcTemplate jdbcTemplate;

    public MerchantService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listMerchants() {
        return jdbcTemplate.queryForList(
            """
            SELECT
                id,
                name,
                contact_phone AS contactPhone,
                email,
                address,
                status,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM merchant
            ORDER BY id DESC
            """
        );
    }

    public void createMerchant(String name, String contactPhone, String email, String address) {
        jdbcTemplate.update(
            """
            INSERT INTO merchant(name, contact_phone, email, address, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
            """,
            name, contactPhone, email, address
        );
    }

    public void updateMerchant(Long id, String name, String contactPhone, String email, String address, String status) {
        int updated = jdbcTemplate.update(
            """
            UPDATE merchant
            SET name = ?, contact_phone = ?, email = ?, address = ?, status = ?, updated_at = NOW()
            WHERE id = ?
            """,
            name, contactPhone, email, address, status, id
        );
        if (updated == 0) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "商家不存在: " + id);
        }
    }
}
