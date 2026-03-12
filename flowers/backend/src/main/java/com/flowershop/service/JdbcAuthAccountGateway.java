package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAuthAccountGateway implements AuthAccountGateway {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthAccountGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void ensureSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS auth_account (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              login_type VARCHAR(16) NOT NULL,
              account VARCHAR(64) NOT NULL,
              password_digest VARCHAR(255) NOT NULL,
              display_name VARCHAR(64) NOT NULL,
              role_code VARCHAR(32) NOT NULL,
              enabled TINYINT NOT NULL DEFAULT 1,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              UNIQUE KEY uk_auth_login_account (login_type, account),
              INDEX idx_auth_role (role_code),
              INDEX idx_auth_enabled (enabled)
            )
            """
        );
    }

    @Override
    public Optional<AuthAccount> findByLoginTypeAndAccount(String loginType, String account) {
        List<AuthAccount> accounts = jdbcTemplate.query(
            """
            SELECT login_type, account, password_digest, display_name, role_code, enabled
            FROM auth_account
            WHERE login_type = ? AND account = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new AuthAccount(
                rs.getString("login_type"),
                rs.getString("account"),
                rs.getString("password_digest"),
                rs.getString("display_name"),
                rs.getString("role_code"),
                rs.getBoolean("enabled")
            ),
            loginType,
            account
        );
        return accounts.stream().findFirst();
    }

    @Override
    public void save(AuthAccount authAccount) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_account(login_type, account, password_digest, display_name, role_code, enabled, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              password_digest = VALUES(password_digest),
              display_name = VALUES(display_name),
              role_code = VALUES(role_code),
              enabled = VALUES(enabled),
              updated_at = NOW()
            """,
            authAccount.loginType(),
            authAccount.account(),
            authAccount.passwordDigest(),
            authAccount.displayName(),
            authAccount.roleCode(),
            authAccount.enabled() ? 1 : 0
        );
    }

    @Override
    public void updatePasswordDigest(String loginType, String account, String passwordDigest) {
        jdbcTemplate.update(
            """
            UPDATE auth_account
            SET password_digest = ?, updated_at = NOW()
            WHERE login_type = ? AND account = ?
            """,
            passwordDigest,
            loginType,
            account
        );
    }
}