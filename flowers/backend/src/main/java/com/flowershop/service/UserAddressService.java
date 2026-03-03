package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class UserAddressService {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");

    private final JdbcTemplate jdbcTemplate;

    public UserAddressService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS user_address (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                receiver_name VARCHAR(64) NOT NULL,
                receiver_phone VARCHAR(20) NOT NULL,
                province VARCHAR(64) NOT NULL DEFAULT '',
                city VARCHAR(64) NOT NULL DEFAULT '',
                district VARCHAR(64) NOT NULL DEFAULT '',
                detail VARCHAR(255) NOT NULL,
                is_default TINYINT NOT NULL DEFAULT 0,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
                INDEX idx_user_address_user (user_id),
                INDEX idx_user_address_default (user_id, is_default)
            )
            """
        );
    }

    public List<Map<String, Object>> listAddresses(Long userId) {
        ensureUserExists(userId);
        return jdbcTemplate.queryForList(
            """
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail,
                   is_default AS isDefault,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM user_address
            WHERE user_id = ?
            ORDER BY is_default DESC, updated_at DESC, id DESC
            """,
            userId
        );
    }

    public Map<String, Object> getAddress(Long userId, Long addressId) {
        ensureUserExists(userId);
        return findAddress(userId, addressId);
    }

    public Map<String, Object> getDefaultAddress(Long userId) {
        ensureUserExists(userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail,
                   is_default AS isDefault,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM user_address
            WHERE user_id = ? AND is_default = 1
            ORDER BY updated_at DESC, id DESC
            LIMIT 1
            """,
            userId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public Map<String, Object> createAddress(Long userId, Map<String, Object> body) {
        ensureUserExists(userId);
        AddressPayload payload = parsePayload(body);
        int totalBefore = countAddresses(userId);
        boolean shouldDefault = payload.isDefault() || totalBefore == 0;
        if (shouldDefault) {
            clearDefault(userId);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO user_address(
                    user_id, receiver_name, receiver_phone, province, city, district, detail, is_default, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setString(2, payload.receiverName());
            ps.setString(3, payload.receiverPhone());
            ps.setString(4, payload.province());
            ps.setString(5, payload.city());
            ps.setString(6, payload.district());
            ps.setString(7, payload.detail());
            ps.setInt(8, shouldDefault ? 1 : 0);
            return ps;
        }, keyHolder);

        if (inserted == 0 || keyHolder.getKey() == null) {
            throw new BusinessException("CREATE_ADDRESS_FAILED", "Failed to create address");
        }

        Long addressId = keyHolder.getKey().longValue();
        ensureOneDefault(userId, addressId);
        return findAddress(userId, addressId);
    }

    @Transactional
    public Map<String, Object> updateAddress(Long userId, Long addressId, Map<String, Object> body) {
        ensureUserExists(userId);
        findAddress(userId, addressId);
        AddressPayload payload = parsePayload(body);
        boolean shouldDefault = payload.isDefault();
        if (shouldDefault) {
            clearDefault(userId);
        }

        int updated = jdbcTemplate.update(
            """
            UPDATE user_address
            SET receiver_name = ?,
                receiver_phone = ?,
                province = ?,
                city = ?,
                district = ?,
                detail = ?,
                is_default = ?,
                updated_at = NOW()
            WHERE id = ? AND user_id = ?
            """,
            payload.receiverName(),
            payload.receiverPhone(),
            payload.province(),
            payload.city(),
            payload.district(),
            payload.detail(),
            shouldDefault ? 1 : 0,
            addressId,
            userId
        );

        if (updated == 0) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found: " + addressId);
        }

        ensureOneDefault(userId, addressId);
        return findAddress(userId, addressId);
    }

    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        ensureUserExists(userId);
        findAddress(userId, addressId);
        clearDefault(userId);
        setDefaultInternal(userId, addressId);
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        ensureUserExists(userId);
        Map<String, Object> target = findAddress(userId, addressId);
        boolean wasDefault = toBoolean(target.get("isDefault"));

        int deleted = jdbcTemplate.update(
            "DELETE FROM user_address WHERE id = ? AND user_id = ?",
            addressId,
            userId
        );

        if (deleted == 0) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found: " + addressId);
        }

        if (wasDefault) {
            ensureOneDefault(userId, null);
        }
    }

    private Map<String, Object> findAddress(Long userId, Long addressId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
            SELECT id,
                   user_id AS userId,
                   receiver_name AS receiverName,
                   receiver_phone AS receiverPhone,
                   province,
                   city,
                   district,
                   detail,
                   is_default AS isDefault,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM user_address
            WHERE user_id = ? AND id = ?
            LIMIT 1
            """,
            userId,
            addressId
        );

        if (rows.isEmpty()) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found: " + addressId);
        }
        return rows.get(0);
    }

    private void ensureUserExists(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_customer WHERE id = ?",
            Integer.class,
            userId
        );
        if (count == null || count == 0) {
            throw new BusinessException("USER_NOT_FOUND", "\u7528\u6237\u4e0d\u5b58\u5728: " + userId);
        }
    }

    private int countAddresses(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_address WHERE user_id = ?",
            Integer.class,
            userId
        );
        return count == null ? 0 : count;
    }

    private void clearDefault(Long userId) {
        jdbcTemplate.update(
            "UPDATE user_address SET is_default = 0, updated_at = NOW() WHERE user_id = ?",
            userId
        );
    }

    private void setDefaultInternal(Long userId, Long addressId) {
        int updated = jdbcTemplate.update(
            "UPDATE user_address SET is_default = 1, updated_at = NOW() WHERE user_id = ? AND id = ?",
            userId,
            addressId
        );
        if (updated == 0) {
            throw new BusinessException("ADDRESS_NOT_FOUND", "Address not found: " + addressId);
        }
    }

    private void ensureOneDefault(Long userId, Long preferredAddressId) {
        int total = countAddresses(userId);
        if (total == 0) {
            return;
        }

        Integer defaultCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_address WHERE user_id = ? AND is_default = 1",
            Integer.class,
            userId
        );
        if (defaultCount != null && defaultCount > 0) {
            return;
        }

        Long targetId = preferredAddressId;
        if (targetId == null) {
            List<Long> ids = jdbcTemplate.query(
                """
                SELECT id
                FROM user_address
                WHERE user_id = ?
                ORDER BY updated_at DESC, id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId
            );
            if (ids.isEmpty()) {
                return;
            }
            targetId = ids.get(0);
        }

        clearDefault(userId);
        setDefaultInternal(userId, targetId);
    }

    private AddressPayload parsePayload(Map<String, Object> body) {
        String receiverName = requireText(body, "receiverName");
        String receiverPhone = requireText(body, "receiverPhone");
        String detail = requireText(body, "detail");
        if (!PHONE_PATTERN.matcher(receiverPhone).matches()) {
            throw new BusinessException("VALIDATION_ERROR", "receiverPhone format invalid");
        }

        return new AddressPayload(
            receiverName,
            receiverPhone,
            readText(body, "province"),
            readText(body, "city"),
            readText(body, "district"),
            detail,
            toBoolean(body.get("isDefault"))
        );
    }

    private static String requireText(Map<String, Object> body, String field) {
        String value = readText(body, field);
        if (value.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", field + " is required");
        }
        return value;
    }

    private static String readText(Map<String, Object> body, String field) {
        if (body == null) {
            return "";
        }
        Object value = body.get(field);
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private record AddressPayload(
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detail,
        boolean isDefault
    ) {
    }
}
