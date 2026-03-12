package com.flowershop.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAuthSessionGateway implements AuthSessionGateway {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthSessionGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void ensureSchema() {
        jdbcTemplate.execute(
            """
            CREATE TABLE IF NOT EXISTS auth_session (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              token VARCHAR(128) NOT NULL,
              login_type VARCHAR(16) NOT NULL,
              account VARCHAR(64) NOT NULL,
              role_code VARCHAR(32) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              UNIQUE KEY uk_auth_session_token (token),
              KEY idx_auth_session_account (login_type, account),
              KEY idx_auth_session_role (role_code)
            )
            """
        );
    }

    @Override
    public void save(AuthSession authSession) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_session(token, login_type, account, role_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
              login_type = VALUES(login_type),
              account = VALUES(account),
              role_code = VALUES(role_code),
              updated_at = NOW()
            """,
            authSession.token(),
            authSession.loginType(),
            authSession.account(),
            authSession.roleCode()
        );
    }

    @Override
    public Optional<AuthSession> findByToken(String token) {
        List<AuthSession> sessions = jdbcTemplate.query(
            """
            SELECT token, login_type, account, role_code
            FROM auth_session
            WHERE token = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new AuthSession(
                rs.getString("token"),
                rs.getString("login_type"),
                rs.getString("account"),
                rs.getString("role_code")
            ),
            token
        );
        return sessions.stream().findFirst();
    }

    @Override
    public void deleteByLoginTypeAndAccount(String loginType, String account) {
        jdbcTemplate.update(
            "DELETE FROM auth_session WHERE login_type = ? AND account = ?",
            loginType,
            account
        );
    }

    @Override
    public void deleteByToken(String token) {
        jdbcTemplate.update("DELETE FROM auth_session WHERE token = ?", token);
    }
}