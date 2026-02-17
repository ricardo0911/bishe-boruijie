package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> loginOrRegister(String openid, String name) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id, openid, name, phone, points, preference_tags, created_at FROM user_customer WHERE openid = ?",
            openid
        );
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO user_customer(openid, name, created_at, updated_at) VALUES (?, ?, NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, openid);
            ps.setString(2, name);
            return ps;
        }, keyHolder);

        Long userId = keyHolder.getKey().longValue();
        return jdbcTemplate.queryForMap(
            "SELECT id, openid, name, phone, points, preference_tags, created_at FROM user_customer WHERE id = ?",
            userId
        );
    }

    public Map<String, Object> getUserById(Long userId) {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT id, openid, name, phone, points, preference_tags, created_at FROM user_customer WHERE id = ?",
            userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在: " + userId);
        }
        return users.get(0);
    }

    public void updateProfile(Long userId, String name, String phone, String preferenceTags) {
        int updated = jdbcTemplate.update(
            "UPDATE user_customer SET name = ?, phone = ?, preference_tags = ?, updated_at = NOW() WHERE id = ?",
            name, phone, preferenceTags, userId
        );
        if (updated == 0) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在: " + userId);
        }
    }

    public List<Map<String, Object>> listAllUsers() {
        return jdbcTemplate.queryForList(
            "SELECT id, openid, name, phone, points, preference_tags, created_at FROM user_customer ORDER BY id DESC"
        );
    }
}
