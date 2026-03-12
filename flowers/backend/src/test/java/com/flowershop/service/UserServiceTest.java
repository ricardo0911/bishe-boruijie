package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void translatesDuplicatePhoneConstraintDuringRegisterIntoBusinessException() {
        UserService userService = new UserService(jdbcTemplate, new PasswordCodec());

        when(jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_customer WHERE account = ?",
            Integer.class,
            "fresh_user"
        )).thenReturn(0);
        when(jdbcTemplate.update(any(PreparedStatementCreator.class), any(KeyHolder.class)))
            .thenThrow(new DuplicateKeyException("Duplicate entry '13800138000' for key 'user_customer.phone'"));

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> userService.register("fresh_user", "pass123456", "Test User", "13800138000")
        );

        assertEquals("USER_PHONE_EXISTS", exception.getCode());
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    void fallsBackToLegacyUsersTableWhenCurrentUserTableHasNoRows() {
        UserService userService = new UserService(jdbcTemplate, new PasswordCodec());

        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq("user_customer")))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM user_customer", Integer.class))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq("users")))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM users", Integer.class))
            .thenReturn(2);
        when(jdbcTemplate.queryForList("SELECT id, openid, account, name, phone, points, created_at AS createdAt FROM user_customer ORDER BY id DESC"))
            .thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM users"))).thenReturn(List.of(
            Map.of(
                "id", 9L,
                "openid", "wx_legacy_9",
                "account", "legacy_user",
                "name", "Legacy User",
                "phone", "13800000009",
                "points", 18,
                "createdAt", Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 10, 30))
            )
        ));

        List<Map<String, Object>> users = userService.listAllUsers();

        assertEquals(1, users.size());
        assertEquals("wx_legacy_9", users.get(0).get("openid"));
        assertEquals("legacy_user", users.get(0).get("account"));
        assertEquals("Legacy User", users.get(0).get("name"));
    }
}
