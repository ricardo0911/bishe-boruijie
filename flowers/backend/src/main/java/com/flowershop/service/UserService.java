package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final String USER_PUBLIC_COLUMNS = "id, openid, account, name, phone, points, created_at AS createdAt";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordCodec passwordCodec;

    public UserService(JdbcTemplate jdbcTemplate, PasswordCodec passwordCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordCodec = passwordCodec;
    }

    @PostConstruct
    public void initializeUserSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS user_customer (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                openid VARCHAR(128) DEFAULT NULL COMMENT 'User OpenID',
                account VARCHAR(64) DEFAULT NULL COMMENT 'Login account',
                password_digest VARCHAR(255) DEFAULT NULL COMMENT 'Password digest',
                name VARCHAR(64) NOT NULL COMMENT 'Display name',
                phone VARCHAR(20) DEFAULT NULL COMMENT 'Phone number',
                points INT NOT NULL DEFAULT 0 COMMENT 'Reward points',
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                KEY idx_user_customer_openid (openid),
                UNIQUE KEY uk_user_customer_account (account)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='User table'
            """
        );

        addColumnIfMissing("account", "ALTER TABLE user_customer ADD COLUMN account VARCHAR(64) DEFAULT NULL COMMENT 'Login account' AFTER openid");
        addColumnIfMissing("password_digest", "ALTER TABLE user_customer ADD COLUMN password_digest VARCHAR(255) DEFAULT NULL COMMENT 'Password digest' AFTER account");
        addColumnIfMissing("updated_at", "ALTER TABLE user_customer ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");

        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_customer'
              AND COLUMN_NAME = 'preference_tags'
            """,
            Integer.class
        );
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE user_customer DROP COLUMN preference_tags");
        }

        jdbcTemplate.execute("UPDATE user_customer SET account = NULL WHERE account IS NOT NULL AND TRIM(account) = ''");
        addIndexIfMissing("uk_user_customer_account", "CREATE UNIQUE INDEX uk_user_customer_account ON user_customer(account)");
        addIndexIfMissing("idx_user_customer_openid", "CREATE INDEX idx_user_customer_openid ON user_customer(openid)");
    }

    public Map<String, Object> register(String account, String password, String name, String phone) {
        String normalizedAccount = normalizeAccount(account);
        validatePassword(password);
        String normalizedName = normalizeName(name, normalizedAccount);
        String normalizedPhone = normalizePhone(phone);

        if (existsByAccount(normalizedAccount)) {
            throw new BusinessException("USER_ACCOUNT_EXISTS", "Account already exists");
        }
        if (hasText(normalizedPhone) && existsByPhone(normalizedPhone, null)) {
            throw new BusinessException("USER_PHONE_EXISTS", "Phone already in use");
        }

        String passwordDigest = passwordCodec.encode(password);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_customer(openid, account, password_digest, name, phone, points, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())",
                    Statement.RETURN_GENERATED_KEYS
                );
                ps.setString(1, "acct_" + normalizedAccount);
                ps.setString(2, normalizedAccount);
                ps.setString(3, passwordDigest);
                ps.setString(4, normalizedName);
                ps.setString(5, normalizedPhone);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw translateDuplicateUserConstraint(ex, "USER_REGISTER_FAILED", "Register failed, please retry later");
        }

        Number userId = keyHolder.getKey();
        if (userId == null) {
            throw new BusinessException("USER_REGISTER_FAILED", "Register failed, please retry later");
        }
        return getUserById(userId.longValue());
    }

    public Map<String, Object> login(String account, String password) {
        String normalizedAccount = normalizeAccount(account);
        validatePassword(password);

        Map<String, Object> user = findUserWithPasswordByAccount(normalizedAccount);
        String passwordDigest = stringValue(user.get("password_digest"));
        if (!hasText(passwordDigest) || !passwordCodec.matches(password, passwordDigest)) {
            throw new BusinessException("USER_LOGIN_FAILED", "Invalid account or password");
        }
        return sanitizeUser(user);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("USER_NOT_FOUND", "User not found");
        }
        validatePassword(oldPassword);
        validatePassword(newPassword);

        Map<String, Object> user = findUserWithPasswordById(userId);
        String passwordDigest = stringValue(user.get("password_digest"));
        if (!hasText(passwordDigest)) {
            throw new BusinessException("USER_PASSWORD_NOT_SET", "Password is not set for this account");
        }
        if (!passwordCodec.matches(oldPassword, passwordDigest)) {
            throw new BusinessException("USER_OLD_PASSWORD_INVALID", "Old password is incorrect");
        }

        jdbcTemplate.update(
            "UPDATE user_customer SET password_digest = ?, updated_at = NOW() WHERE id = ?",
            passwordCodec.encode(newPassword), userId
        );
    }

    public Map<String, Object> getUserById(Long userId) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT " + USER_PUBLIC_COLUMNS + " FROM user_customer WHERE id = ?",
            userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("USER_NOT_FOUND", "User not found: " + userId);
        }
        return sanitizeUser(users.get(0));
    }

    public void updateProfile(Long userId, String name, String phone) {
        String normalizedName = normalizeName(name, null);
        String normalizedPhone = normalizePhone(phone);
        if (hasText(normalizedPhone) && existsByPhone(normalizedPhone, userId)) {
            throw new BusinessException("USER_PHONE_EXISTS", "Phone already in use");
        }

        int updated;
        try {
            updated = jdbcTemplate.update(
                "UPDATE user_customer SET name = ?, phone = ?, updated_at = NOW() WHERE id = ?",
                normalizedName, normalizedPhone, userId
            );
        } catch (DuplicateKeyException ex) {
            throw translateDuplicateUserConstraint(ex, "USER_PROFILE_UPDATE_FAILED", "Profile update failed, please retry later");
        }
        if (updated == 0) {
            throw new BusinessException("USER_NOT_FOUND", "User not found: " + userId);
        }
    }

    public List<Map<String, Object>> listAllUsers() {
        String tableName = resolvePreferredTable("user_customer", "users");
        if ("users".equals(tableName)) {
            return jdbcTemplate.queryForList(
                """
                SELECT
                    id,
                    openid,
                    username AS account,
                    COALESCE(NULLIF(nickname, ''), username) AS name,
                    phone,
                    points,
                    created_at AS createdAt
                FROM users
                ORDER BY id DESC
                """
            ).stream().map(this::sanitizeUser).toList();
        }
        if (!"user_customer".equals(tableName)) {
            return List.of();
        }
        return jdbcTemplate.queryForList(
            "SELECT " + USER_PUBLIC_COLUMNS + " FROM user_customer ORDER BY id DESC"
        ).stream().map(this::sanitizeUser).toList();
    }

    private boolean existsByAccount(String account) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_customer WHERE account = ?",
            Integer.class,
            account
        );
        return count != null && count > 0;
    }

    private boolean existsByPhone(String phone, Long excludeUserId) {
        if (!hasText(phone)) {
            return false;
        }
        Integer count;
        if (excludeUserId == null) {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_customer WHERE phone = ?",
                Integer.class,
                phone
            );
        } else {
            count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM user_customer WHERE phone = ? AND id <> ?",
                Integer.class,
                phone,
                excludeUserId
            );
        }
        return count != null && count > 0;
    }

    private Map<String, Object> findUserWithPasswordByAccount(String account) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT id, account, password_digest, name, phone, points, created_at AS createdAt FROM user_customer WHERE account = ?",
            account
        );
        if (users.isEmpty()) {
            throw new BusinessException("USER_LOGIN_FAILED", "Invalid account or password");
        }
        return users.get(0);
    }

    private Map<String, Object> findUserWithPasswordById(Long userId) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT id, account, password_digest, name, phone, points, created_at AS createdAt FROM user_customer WHERE id = ?",
            userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("USER_NOT_FOUND", "User not found: " + userId);
        }
        return users.get(0);
    }

    private Map<String, Object> sanitizeUser(Map<String, Object> row) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", row.get("id"));
        user.put("openid", row.get("openid"));
        user.put("account", row.get("account"));
        user.put("name", row.get("name"));
        user.put("phone", row.get("phone"));
        user.put("points", row.get("points"));
        user.put("createdAt", row.get("createdAt"));
        return user;
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
    private void addColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_customer'
              AND COLUMN_NAME = ?
            """,
            Integer.class,
            columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addIndexIfMissing(String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(1)
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_customer'
              AND INDEX_NAME = ?
            """,
            Integer.class,
            indexName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }

    private String normalizeAccount(String account) {
        String normalized = stringValue(account).trim();
        if (!normalized.matches("[A-Za-z0-9_]{4,32}")) {
            throw new BusinessException("USER_ACCOUNT_INVALID", "Account must be 4-32 letters, numbers, or underscores");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        String normalized = stringValue(password);
        if (normalized.length() < 6 || normalized.length() > 32) {
            throw new BusinessException("USER_PASSWORD_INVALID", "Password length must be 6-32 characters");
        }
    }

    private String normalizeName(String name, String fallbackAccount) {
        String normalized = stringValue(name).trim();
        if (!hasText(normalized)) {
            normalized = hasText(fallbackAccount) ? fallbackAccount : "Flower User";
        }
        if (normalized.length() > 32) {
            throw new BusinessException("USER_NAME_INVALID", "Display name cannot exceed 32 characters");
        }
        return normalized;
    }

    private String normalizePhone(String phone) {
        String normalized = stringValue(phone).trim();
        if (!hasText(normalized)) {
            return null;
        }
        if (!normalized.matches("1\\d{10}")) {
            throw new BusinessException("USER_PHONE_INVALID", "Phone format is invalid");
        }
        return normalized;
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BusinessException translateDuplicateUserConstraint(DuplicateKeyException ex, String fallbackCode, String fallbackMessage) {
        String message = stringValue(ex.getMessage()).toLowerCase();
        if (message.contains("user_customer.phone") || message.contains("uk_user_customer_phone") || message.contains("phone")) {
            return new BusinessException("USER_PHONE_EXISTS", "Phone already in use");
        }
        if (message.contains("user_customer.account") || message.contains("uk_user_customer_account") || message.contains("account")) {
            return new BusinessException("USER_ACCOUNT_EXISTS", "Account already exists");
        }
        return new BusinessException(fallbackCode, fallbackMessage);
    }
}
