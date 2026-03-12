package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantService {

    private final JdbcTemplate jdbcTemplate;

    public MerchantService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listMerchants() {
        String tableName = resolvePreferredTable("merchant", "merchants");
        if ("merchants".equals(tableName)) {
            return jdbcTemplate.queryForList(
                """
                SELECT
                    id,
                    shop_name AS name,
                    contact_phone AS contactPhone,
                    NULL AS email,
                    address,
                    CASE
                        WHEN status = 1 THEN 'ACTIVE'
                        WHEN status = 0 THEN 'DISABLED'
                        ELSE 'PENDING'
                    END AS status,
                    created_at AS createdAt,
                    updated_at AS updatedAt
                FROM merchants
                ORDER BY id DESC
                """
            );
        }
        if (!"merchant".equals(tableName)) {
            return List.of();
        }
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

    public List<Map<String, Object>> listPublicMerchants() {
        return listMerchants().stream()
            .filter(item -> "ACTIVE".equalsIgnoreCase(stringValue(item.get("status"))))
            .map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.get("id"));
                row.put("name", item.get("name"));
                row.put("contactPhone", item.get("contactPhone"));
                row.put("address", item.get("address"));
                return row;
            })
            .toList();
    }

    public Map<String, Object> getPublicSupportInfo() {
        List<Map<String, Object>> merchants = listMerchants();
        Map<String, Object> selected = merchants.stream()
            .filter(item -> "ACTIVE".equalsIgnoreCase(stringValue(item.get("status"))))
            .findFirst()
            .orElse(merchants.isEmpty() ? Map.of() : merchants.get(0));

        Map<String, Object> supportInfo = new LinkedHashMap<>();
        supportInfo.put("name", defaultIfBlank(stringValue(selected.get("name")), "\u82b1\u4e4b\u90fd\u5b98\u65b9\u82b1\u5e97"));
        supportInfo.put("contactPhone", defaultIfBlank(stringValue(selected.get("contactPhone")), "400-800-1314"));
        supportInfo.put("email", stringValue(selected.get("email")));
        supportInfo.put("address", defaultIfBlank(stringValue(selected.get("address")), "\u5317\u4eac\u5e02\u671d\u9633\u533a\u671b\u4eac\u82b1\u793c\u4e2d\u5fc3"));
        supportInfo.put("serviceHours", "09:00-21:00");
        supportInfo.put("serviceDesc", "\u5728\u7ebf\u5ba2\u670d\u3001\u7535\u8bdd\u54a8\u8be2\u4e0e\u552e\u540e\u5904\u7406");
        return supportInfo;
    }

    public void createMerchant(String name, String contactPhone, String email, String address) {
        String tableName = resolveWritableTable("merchant", "merchants");
        if ("merchants".equals(tableName)) {
            jdbcTemplate.update(
                """
                INSERT INTO merchants(shop_name, contact_phone, address, status, created_at, updated_at)
                VALUES (?, ?, ?, 1, NOW(), NOW())
                """,
                name, contactPhone, address
            );
            return;
        }
        jdbcTemplate.update(
            """
            INSERT INTO merchant(name, contact_phone, email, address, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
            """,
            name, contactPhone, email, address
        );
    }

    public void updateMerchant(Long id, String name, String contactPhone, String email, String address, String status) {
        int updated;
        String tableName = resolveWritableTable("merchant", "merchants");
        if ("merchants".equals(tableName)) {
            updated = jdbcTemplate.update(
                """
                UPDATE merchants
                SET shop_name = ?, contact_phone = ?, address = ?, status = ?, updated_at = NOW()
                WHERE id = ?
                """,
                name, contactPhone, address, toLegacyStatus(status), id
            );
        } else {
            updated = jdbcTemplate.update(
                """
                UPDATE merchant
                SET name = ?, contact_phone = ?, email = ?, address = ?, status = ?, updated_at = NOW()
                WHERE id = ?
                """,
                name, contactPhone, email, address, status, id
            );
        }
        if (updated == 0) {
            throw new BusinessException("MERCHANT_NOT_FOUND", "閸熷棗顔嶆稉宥呯摠閸? " + id);
        }
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

    private String resolveWritableTable(String currentTable, String legacyTable) {
        if (tableExists(currentTable)) {
            return currentTable;
        }
        if (tableExists(legacyTable)) {
            return legacyTable;
        }
        return currentTable;
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int toLegacyStatus(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return 1;
        }
        if ("DISABLED".equalsIgnoreCase(status)) {
            return 0;
        }
        return 2;
    }
}
