-- 售后记录表
-- 用于存储退款申请、审核、处理的全流程记录

CREATE TABLE IF NOT EXISTS after_sale_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    refund_no VARCHAR(32) NOT NULL COMMENT '退款单号，格式：RFyyyyMMddHHmmssXXXX',
    order_no VARCHAR(32) NOT NULL COMMENT '关联订单号',
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    refund_amount DECIMAL(12, 2) NOT NULL COMMENT '退款金额',
    reason VARCHAR(255) NOT NULL COMMENT '退款原因',
    description TEXT COMMENT '退款说明',
    evidence_images VARCHAR(2000) COMMENT '凭证图片，JSON数组格式存储URL列表',
    status VARCHAR(32) NOT NULL COMMENT '状态：REFUND_REQUESTED(申请中)、REFUNDING(退款中)、REFUNDED(已退款)、REJECTED(已拒绝)、REFUND_FAILED(退款失败)',
    reject_reason VARCHAR(500) COMMENT '拒绝原因',
    transaction_id VARCHAR(64) COMMENT '微信支付退款单号',
    apply_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    audit_time DATETIME COMMENT '审核时间',
    refund_time DATETIME COMMENT '退款完成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    UNIQUE KEY uk_refund_no (refund_no),
    INDEX idx_order_id (order_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status),
    INDEX idx_apply_time (apply_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后记录表';

-- 订单状态扩展（需要在customer_order表中支持以下状态）
-- REFUND_REQUESTED: 退款申请中
-- REFUNDING: 退款中
-- REFUNDED: 已退款
