package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MerchantServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void fallsBackToLegacyMerchantsTableWhenCurrentMerchantTableHasNoRows() {
        MerchantService merchantService = new MerchantService(jdbcTemplate);

        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq("merchant")))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM merchant", Integer.class))
            .thenReturn(0);
        when(jdbcTemplate.queryForObject(contains("information_schema.TABLES"), eq(Integer.class), eq("merchants")))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM merchants", Integer.class))
            .thenReturn(3);
        when(jdbcTemplate.queryForList(contains("FROM merchants"))).thenReturn(List.of(
            Map.of(
                "id", 7L,
                "name", "Legacy Merchant",
                "contactPhone", "13900000007",
                "address", "Shanghai Pudong",
                "status", "ACTIVE",
                "createdAt", Timestamp.valueOf(LocalDateTime.of(2026, 2, 18, 9, 0)),
                "updatedAt", Timestamp.valueOf(LocalDateTime.of(2026, 3, 2, 9, 0))
            )
        ));

        List<Map<String, Object>> merchants = merchantService.listMerchants();

        assertEquals(1, merchants.size());
        assertEquals("Legacy Merchant", merchants.get(0).get("name"));
        assertEquals("ACTIVE", merchants.get(0).get("status"));
    }
}
