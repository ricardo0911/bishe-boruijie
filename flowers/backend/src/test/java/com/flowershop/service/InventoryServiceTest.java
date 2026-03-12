package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void adjustAvailableStockCanReduceInventoryToZero() throws Exception {
        InventoryService inventoryService = new InventoryService(jdbcTemplate);
        stubInventoryBatches(
            101L,
            List.of(
                new BatchRow(11L, new BigDecimal("6"), new BigDecimal("1")),
                new BatchRow(12L, new BigDecimal("7"), BigDecimal.ZERO)
            )
        );
        when(jdbcTemplate.update(
            contains("SET current_qty = current_qty - ?"),
            eq(new BigDecimal("5")),
            eq(11L),
            eq(new BigDecimal("5"))
        )).thenReturn(1);
        when(jdbcTemplate.update(
            contains("SET current_qty = current_qty - ?"),
            eq(new BigDecimal("7")),
            eq(12L),
            eq(new BigDecimal("7"))
        )).thenReturn(1);

        inventoryService.adjustAvailableStock(101L, 0, "COUNT_ERROR", 7, "A");

        verify(jdbcTemplate).update(
            contains("SET current_qty = current_qty - ?"),
            eq(new BigDecimal("5")),
            eq(11L),
            eq(new BigDecimal("5"))
        );
        verify(jdbcTemplate).update(
            contains("SET current_qty = current_qty - ?"),
            eq(new BigDecimal("7")),
            eq(12L),
            eq(new BigDecimal("7"))
        );
        verify(jdbcTemplate, never()).update(
            contains("INSERT INTO inventory_batch"),
            any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void adjustAvailableStockAddsNewBatchWhenTargetIsHigher() throws Exception {
        InventoryService inventoryService = new InventoryService(jdbcTemplate);
        stubInventoryBatches(
            202L,
            List.of(new BatchRow(21L, new BigDecimal("3"), BigDecimal.ZERO))
        );
        when(jdbcTemplate.query(
            contains("SELECT cost_price FROM flower_material"),
            any(RowMapper.class),
            eq(202L)
        )).thenReturn(List.of(new BigDecimal("12.50")));
        when(jdbcTemplate.update(
            contains("INSERT INTO inventory_batch"),
            eq(202L),
            eq("COUNT_ERROR"),
            eq("A"),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(4),
            eq(4),
            eq(new BigDecimal("12.50"))
        )).thenReturn(1);

        inventoryService.adjustAvailableStock(202L, 7, "COUNT_ERROR", 7, "A");

        verify(jdbcTemplate).update(
            contains("INSERT INTO inventory_batch"),
            eq(202L),
            eq("COUNT_ERROR"),
            eq("A"),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(4),
            eq(4),
            eq(new BigDecimal("12.50"))
        );
    }

    private void stubInventoryBatches(Long flowerId, List<BatchRow> rows) throws Exception {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = invocation.getArgument(1);
            List<Object> mappedRows = new ArrayList<>();

            for (int index = 0; index < rows.size(); index++) {
                BatchRow row = rows.get(index);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(row.batchId());
                when(rs.getBigDecimal("current_qty")).thenReturn(row.currentQty());
                when(rs.getBigDecimal("locked_qty")).thenReturn(row.lockedQty());
                mappedRows.add(rowMapper.mapRow(rs, index));
            }

            return mappedRows;
        }).when(jdbcTemplate).query(contains("FROM inventory_batch"), any(RowMapper.class), eq(flowerId));
    }

    private record BatchRow(Long batchId, BigDecimal currentQty, BigDecimal lockedQty) {
    }
}
