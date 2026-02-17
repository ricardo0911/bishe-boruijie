package com.flowershop.service;

import com.flowershop.dto.BatchStockResponse;
import com.flowershop.dto.InventoryAlertResponse;
import com.flowershop.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockMaterials(Long orderId, String orderNo, Map<Long, BigDecimal> flowerDemand, LocalDateTime expiresAt) {
        for (Map.Entry<Long, BigDecimal> entry : flowerDemand.entrySet()) {
            Long flowerId = entry.getKey();
            BigDecimal demandQty = entry.getValue();
            if (demandQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal remain = demandQty;
            List<BatchStockInternal> batches = jdbcTemplate.query(
                """
                SELECT id, current_qty, locked_qty
                FROM inventory_batch
                WHERE flower_id = ?
                  AND current_qty > locked_qty
                ORDER BY wilt_time ASC, receipt_time ASC
                FOR UPDATE
                """,
                (rs, rowNum) -> new BatchStockInternal(
                    rs.getLong("id"),
                    rs.getBigDecimal("current_qty"),
                    rs.getBigDecimal("locked_qty")
                ),
                flowerId
            );

            for (BatchStockInternal batch : batches) {
                BigDecimal available = batch.currentQty().subtract(batch.lockedQty());
                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal lockQty = min(available, remain);
                if (lockQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                int updated = jdbcTemplate.update(
                    """
                    UPDATE inventory_batch
                    SET locked_qty = locked_qty + ?, updated_at = NOW()
                    WHERE id = ?
                    """,
                    lockQty,
                    batch.batchId()
                );
                if (updated == 0) {
                    throw new BusinessException("LOCK_FAILED", "库存锁定失败，批次不可用: " + batch.batchId());
                }

                jdbcTemplate.update(
                    """
                    INSERT INTO stock_lock(order_id, order_no, flower_id, batch_id, lock_qty, status, expires_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'LOCKED', ?, NOW(), NOW())
                    """,
                    orderId,
                    orderNo,
                    flowerId,
                    batch.batchId(),
                    lockQty,
                    Timestamp.valueOf(expiresAt)
                );

                remain = remain.subtract(lockQty);
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }

            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                String flowerName = findFlowerName(flowerId);
                throw new BusinessException("INSUFFICIENT_STOCK", "库存不足: " + flowerName + " 仍缺 " + remain);
            }
        }
    }

    public void confirmLockedMaterials(Long orderId) {
        List<LockRow> locks = jdbcTemplate.query(
            """
            SELECT id, batch_id, lock_qty
            FROM stock_lock
            WHERE order_id = ? AND status = 'LOCKED'
            FOR UPDATE
            """,
            (rs, rowNum) -> new LockRow(
                rs.getLong("id"),
                rs.getLong("batch_id"),
                rs.getBigDecimal("lock_qty")
            ),
            orderId
        );

        if (locks.isEmpty()) {
            throw new BusinessException("LOCK_NOT_FOUND", "未找到可确认的锁库存记录");
        }

        for (LockRow lock : locks) {
            int updated = jdbcTemplate.update(
                """
                UPDATE inventory_batch
                SET current_qty = current_qty - ?,
                    locked_qty = locked_qty - ?,
                    updated_at = NOW()
                WHERE id = ?
                  AND locked_qty >= ?
                  AND current_qty >= ?
                """,
                lock.lockQty(),
                lock.lockQty(),
                lock.batchId(),
                lock.lockQty(),
                lock.lockQty()
            );
            if (updated == 0) {
                throw new BusinessException("CONFIRM_LOCK_FAILED", "确认扣减失败，批次库存异常: " + lock.batchId());
            }

            jdbcTemplate.update(
                "UPDATE stock_lock SET status = 'CONFIRMED', updated_at = NOW() WHERE id = ?",
                lock.id()
            );
        }
    }

    public void releaseLockedMaterials(Long orderId) {
        List<LockRow> locks = jdbcTemplate.query(
            """
            SELECT id, batch_id, lock_qty
            FROM stock_lock
            WHERE order_id = ? AND status = 'LOCKED'
            FOR UPDATE
            """,
            (rs, rowNum) -> new LockRow(
                rs.getLong("id"),
                rs.getLong("batch_id"),
                rs.getBigDecimal("lock_qty")
            ),
            orderId
        );

        for (LockRow lock : locks) {
            int updated = jdbcTemplate.update(
                """
                UPDATE inventory_batch
                SET locked_qty = locked_qty - ?, updated_at = NOW()
                WHERE id = ? AND locked_qty >= ?
                """,
                lock.lockQty(),
                lock.batchId(),
                lock.lockQty()
            );
            if (updated == 0) {
                throw new BusinessException("RELEASE_LOCK_FAILED", "释放锁库存失败，批次库存异常: " + lock.batchId());
            }

            jdbcTemplate.update(
                "UPDATE stock_lock SET status = 'RELEASED', updated_at = NOW() WHERE id = ?",
                lock.id()
            );
        }
    }

    public void rollbackConfirmedMaterials(Long orderId) {
        List<LockRow> locks = jdbcTemplate.query(
            """
            SELECT id, batch_id, lock_qty
            FROM stock_lock
            WHERE order_id = ? AND status = 'CONFIRMED'
            FOR UPDATE
            """,
            (rs, rowNum) -> new LockRow(
                rs.getLong("id"),
                rs.getLong("batch_id"),
                rs.getBigDecimal("lock_qty")
            ),
            orderId
        );

        for (LockRow lock : locks) {
            int updated = jdbcTemplate.update(
                """
                UPDATE inventory_batch
                SET current_qty = current_qty + ?, updated_at = NOW()
                WHERE id = ?
                """,
                lock.lockQty(),
                lock.batchId()
            );
            if (updated == 0) {
                throw new BusinessException("ROLLBACK_STOCK_FAILED", "退款回滚库存失败，批次不存在: " + lock.batchId());
            }

            jdbcTemplate.update(
                "UPDATE stock_lock SET status = 'ROLLED_BACK', updated_at = NOW() WHERE id = ?",
                lock.id()
            );
        }
    }

    public List<InventoryAlertResponse> listLowStockAlerts() {
        return jdbcTemplate.query(
            """
            SELECT
                f.id AS flower_id,
                f.name AS flower_name,
                ROUND(COALESCE(SUM(b.current_qty - b.locked_qty), 0), 2) AS available_qty,
                f.warn_threshold,
                CASE
                    WHEN ROUND(COALESCE(SUM(b.current_qty - b.locked_qty), 0), 2) <= 0 THEN 'OUT_OF_STOCK'
                    ELSE 'LOW_STOCK'
                END AS warning_level
            FROM flower_material f
            LEFT JOIN inventory_batch b ON f.id = b.flower_id
            WHERE f.enabled = 1
            GROUP BY f.id, f.name, f.warn_threshold
            HAVING available_qty <= f.warn_threshold
            ORDER BY available_qty ASC
            """,
            (rs, rowNum) -> new InventoryAlertResponse(
                rs.getLong("flower_id"),
                rs.getString("flower_name"),
                rs.getBigDecimal("available_qty"),
                rs.getBigDecimal("warn_threshold"),
                rs.getString("warning_level")
            )
        );
    }

    public List<BatchStockResponse> listBatchesByFefo(Long flowerId) {
        return jdbcTemplate.query(
            """
            SELECT
                id,
                flower_id,
                quality_status,
                receipt_time,
                wilt_time,
                current_qty,
                locked_qty,
                (current_qty - locked_qty) AS available_qty,
                supplier_name
            FROM inventory_batch
            WHERE flower_id = ?
            ORDER BY wilt_time ASC, receipt_time ASC
            """,
            (rs, rowNum) -> new BatchStockResponse(
                rs.getLong("id"),
                rs.getLong("flower_id"),
                rs.getString("quality_status"),
                toLocalDateTime(rs.getTimestamp("receipt_time")),
                toLocalDateTime(rs.getTimestamp("wilt_time")),
                rs.getBigDecimal("current_qty"),
                rs.getBigDecimal("locked_qty"),
                rs.getBigDecimal("available_qty"),
                rs.getString("supplier_name")
            ),
            flowerId
        );
    }

    public void addBatch(Long flowerId, String supplierName, int quantity, int shelfLifeDays, String qualityStatus) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime wiltTime = now.plusDays(shelfLifeDays);

        // look up cost_price from flower_material for unit_cost default
        List<BigDecimal> costs = jdbcTemplate.query(
            "SELECT cost_price FROM flower_material WHERE id = ?",
            (rs, rowNum) -> rs.getBigDecimal("cost_price"),
            flowerId
        );
        BigDecimal unitCost = costs.isEmpty() ? BigDecimal.ZERO : costs.get(0);

        jdbcTemplate.update(
            """
            INSERT INTO inventory_batch(flower_id, supplier_name, quality_status, receipt_time, wilt_time,
                                        original_qty, current_qty, locked_qty, unit_cost, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, NOW(), NOW())
            """,
            flowerId, supplierName, qualityStatus,
            Timestamp.valueOf(now), Timestamp.valueOf(wiltTime), quantity, quantity, unitCost
        );
    }

    private String findFlowerName(Long flowerId) {
        List<String> names = jdbcTemplate.query(
            "SELECT name FROM flower_material WHERE id = ?",
            (rs, rowNum) -> rs.getString("name"),
            flowerId
        );
        return names.isEmpty() ? "flower#" + flowerId : names.get(0);
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record BatchStockInternal(Long batchId, BigDecimal currentQty, BigDecimal lockedQty) {
    }

    private record LockRow(Long id, Long batchId, BigDecimal lockQty) {
    }
}
