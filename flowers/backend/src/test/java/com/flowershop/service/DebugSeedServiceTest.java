package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DebugSeedServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProductService productService;

    @Test
    void seedInventoryBatchesCreatesDebugStockForAllBomMaterials() {
        DebugSeedService debugSeedService = new DebugSeedService(jdbcTemplate, productService);

        int inserted = debugSeedService.seedInventoryBatches();

        assertEquals(6, inserted);
        verify(jdbcTemplate).update("DELETE FROM inventory_batch WHERE supplier_name LIKE 'DEBUG_STOCK_%'");
        verify(jdbcTemplate, times(6)).update(
            contains("INSERT INTO inventory_batch"),
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }
}
