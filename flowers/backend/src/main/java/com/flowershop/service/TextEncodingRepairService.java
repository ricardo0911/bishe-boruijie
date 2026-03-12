package com.flowershop.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TextEncodingRepairService {

    private static final Logger log = LoggerFactory.getLogger(TextEncodingRepairService.class);
    private static final Pattern MOJIBAKE_MARKER = Pattern.compile("[\\u00C0-\\u00FF]");
    private static final Pattern CJK_MARKER = Pattern.compile("[\\u4E00-\\u9FFF]");

    private final JdbcTemplate jdbcTemplate;

    public TextEncodingRepairService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void repairMojibakeText() {
        int updatedRows = 0;
        updatedRows += repairTable("product", "id", List.of("title", "description"));
        updatedRows += repairTable("order_item", "id", List.of("product_title"));
        updatedRows += repairTable("customer_order", "id", List.of("receiver_name", "receiver_address", "remark"));
        updatedRows += repairTable("user_customer", "id", List.of("name"));
        updatedRows += repairTable("user_address", "id", List.of("receiver_name", "province", "city", "district", "detail"));
        updatedRows += repairTable("after_sale_record", "id", List.of("reason", "description", "reject_reason"));

        if (updatedRows > 0) {
            log.warn("Detected and repaired mojibake text rows: {}", updatedRows);
        }
    }

    private int repairTable(String tableName, String idColumn, List<String> targetColumns) {
        if (!columnExists(tableName, idColumn)) {
            return 0;
        }

        List<String> existingColumns = targetColumns.stream()
            .filter(column -> columnExists(tableName, column))
            .collect(Collectors.toList());
        if (existingColumns.isEmpty()) {
            return 0;
        }

        String selectSql = "SELECT " + quote(idColumn) + ", " +
            existingColumns.stream().map(this::quote).collect(Collectors.joining(", ")) +
            " FROM " + quote(tableName);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql);

        int updatedRows = 0;
        for (Map<String, Object> row : rows) {
            Object idValue = row.get(idColumn);
            if (!(idValue instanceof Number idNumber)) {
                continue;
            }

            Map<String, Object> changed = new LinkedHashMap<>();
            for (String column : existingColumns) {
                Object rawValue = row.get(column);
                if (!(rawValue instanceof String rawText)) {
                    continue;
                }
                String repairedText = repairUtf8Mojibake(rawText);
                if (!Objects.equals(rawText, repairedText)) {
                    changed.put(column, repairedText);
                }
            }

            if (changed.isEmpty()) {
                continue;
            }

            String updateSql = "UPDATE " + quote(tableName) + " SET " +
                changed.keySet().stream().map(column -> quote(column) + " = ?").collect(Collectors.joining(", ")) +
                " WHERE " + quote(idColumn) + " = ?";

            List<Object> args = new ArrayList<>(changed.values());
            args.add(idNumber.longValue());
            int affected = jdbcTemplate.update(updateSql, args.toArray());
            if (affected > 0) {
                updatedRows += affected;
            }
        }
        return updatedRows;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }

    private String repairUtf8Mojibake(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String fixed = text;
        for (int i = 0; i < 2; i++) {
            if (!MOJIBAKE_MARKER.matcher(fixed).find()) {
                break;
            }
            String decoded = new String(fixed.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            if (decoded.equals(fixed) || decoded.indexOf('\uFFFD') >= 0) {
                break;
            }
            if (!CJK_MARKER.matcher(decoded).find()) {
                break;
            }
            fixed = decoded;
        }
        return fixed;
    }

    private String quote(String name) {
        return "`" + name + "`";
    }
}
