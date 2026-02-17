-- ============================================
-- 花店管理系统 数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS flower_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE flower_shop;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `openid` VARCHAR(128) DEFAULT NULL COMMENT '微信OpenID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `gender` TINYINT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `points` INT DEFAULT 0 COMMENT '积分',
    `preference_tags` VARCHAR(500) DEFAULT NULL COMMENT '偏好标签(JSON)',
    `role` VARCHAR(20) DEFAULT 'USER' COMMENT '角色: USER/MERCHANT/ADMIN',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_openid` (`openid`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户表';

-- ----------------------------
-- 2. 商家表
-- ----------------------------
DROP TABLE IF EXISTS `merchants`;
CREATE TABLE `merchants` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT DEFAULT NULL COMMENT '关联用户ID',
    `shop_name` VARCHAR(100) NOT NULL COMMENT '店铺名称',
    `logo` VARCHAR(500) DEFAULT NULL COMMENT '店铺Logo',
    `contact_name` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
    `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    `address` VARCHAR(300) DEFAULT NULL COMMENT '店铺地址',
    `description` TEXT COMMENT '店铺描述',
    `business_license` VARCHAR(500) DEFAULT NULL COMMENT '营业执照图片',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0禁用 1正常 2审核中',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='商家表';

-- ----------------------------
-- 3. 分类表
-- ----------------------------
DROP TABLE IF EXISTS `categories`;
CREATE TABLE `categories` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `icon` VARCHAR(500) DEFAULT NULL COMMENT '分类图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `parent_id` BIGINT DEFAULT 0 COMMENT '父分类ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB COMMENT='分类表';

-- ----------------------------
-- 4. 花材表 (原材料)
-- ----------------------------
DROP TABLE IF EXISTS `flowers`;
CREATE TABLE `flowers` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL COMMENT '花材名称',
    `category` VARCHAR(50) DEFAULT NULL COMMENT '类别(玫瑰/百合/配叶/包材等)',
    `unit` VARCHAR(20) DEFAULT '枝' COMMENT '计量单位',
    `cost_price` DECIMAL(10,2) NOT NULL COMMENT '进货成本价',
    `sell_price` DECIMAL(10,2) NOT NULL COMMENT '零售单价',
    `shelf_life_days` INT DEFAULT 7 COMMENT '预计花期(天)',
    `alert_threshold` INT DEFAULT 20 COMMENT '库存预警阈值',
    `image` VARCHAR(500) DEFAULT NULL COMMENT '图片',
    `description` TEXT COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '所属商家',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB COMMENT='花材表';

-- ----------------------------
-- 5. 商品表 (面向展示的销售单位)
-- ----------------------------
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL COMMENT '商品标题',
    `subtitle` VARCHAR(300) DEFAULT NULL COMMENT '副标题',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '所属商家',
    `price` DECIMAL(10,2) NOT NULL COMMENT '售价',
    `original_price` DECIMAL(10,2) DEFAULT NULL COMMENT '原价(划线价)',
    `cost_price` DECIMAL(10,2) DEFAULT NULL COMMENT '成本价(BOM自动计算)',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图',
    `images` TEXT COMMENT '详情图(JSON数组)',
    `description` TEXT COMMENT '商品描述',
    `product_type` VARCHAR(20) DEFAULT 'SINGLE' COMMENT 'SINGLE单品/BUNDLE花束/CUSTOM定制',
    `sales_count` INT DEFAULT 0 COMMENT '销量',
    `stock` INT DEFAULT 0 COMMENT '库存(单品模式)/虚拟库存',
    `packaging_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '包装费',
    `delivery_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0下架 1上架',
    `is_recommend` TINYINT DEFAULT 0 COMMENT '是否推荐',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_category` (`category_id`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='商品表';

-- ----------------------------
-- 6. 物料清单 (BOM)
-- ----------------------------
DROP TABLE IF EXISTS `product_bom`;
CREATE TABLE `product_bom` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `flower_id` BIGINT NOT NULL COMMENT '花材ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '用量',
    KEY `idx_product` (`product_id`),
    KEY `idx_flower` (`flower_id`)
) ENGINE=InnoDB COMMENT='物料清单(BOM)';

-- ----------------------------
-- 7. 库存批次表
-- ----------------------------
DROP TABLE IF EXISTS `inventory_batches`;
CREATE TABLE `inventory_batches` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `flower_id` BIGINT NOT NULL COMMENT '花材ID',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '商家ID',
    `supplier_name` VARCHAR(100) DEFAULT NULL COMMENT '供应商',
    `receipt_date` DATETIME NOT NULL COMMENT '入库日期',
    `wilt_date` DATETIME NOT NULL COMMENT '预计枯萎日期',
    `original_qty` INT NOT NULL COMMENT '入库总量',
    `current_qty` INT NOT NULL COMMENT '剩余可用量',
    `cost_per_unit` DECIMAL(10,2) DEFAULT NULL COMMENT '本批次单位成本',
    `quality_status` VARCHAR(10) DEFAULT 'A' COMMENT 'A新鲜/B轻微折损/C临期促销',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_flower` (`flower_id`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_wilt_date` (`wilt_date`)
) ENGINE=InnoDB COMMENT='库存批次表';

-- ----------------------------
-- 8. 购物车
-- ----------------------------
DROP TABLE IF EXISTS `cart_items`;
CREATE TABLE `cart_items` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `selected` TINYINT DEFAULT 1 COMMENT '是否选中',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='购物车';

-- ----------------------------
-- 9. 订单主表
-- ----------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '商家ID',
    `total_amount` DECIMAL(10,2) NOT NULL COMMENT '订单总额',
    `pay_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '实付金额',
    `packaging_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '包装费',
    `delivery_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '配送费',
    `discount_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠金额',
    `status` VARCHAR(30) DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/PROCESSING/DELIVERING/COMPLETED/CANCELLED/REFUNDED',
    `pay_method` VARCHAR(20) DEFAULT NULL COMMENT '支付方式',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `receiver_name` VARCHAR(50) DEFAULT NULL COMMENT '收货人',
    `receiver_phone` VARCHAR(20) DEFAULT NULL COMMENT '收货人电话',
    `receiver_address` VARCHAR(300) DEFAULT NULL COMMENT '收货地址',
    `delivery_time` DATETIME DEFAULT NULL COMMENT '期望配送时间',
    `greeting_card` VARCHAR(500) DEFAULT NULL COMMENT '贺卡留言',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '订单备注',
    `cancel_reason` VARCHAR(300) DEFAULT NULL COMMENT '取消原因',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user` (`user_id`),
    KEY `idx_merchant` (`merchant_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='订单表';

-- ----------------------------
-- 10. 订单明细表
-- ----------------------------
DROP TABLE IF EXISTS `order_items`;
CREATE TABLE `order_items` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `product_title` VARCHAR(200) DEFAULT NULL COMMENT '商品标题(冗余)',
    `product_image` VARCHAR(500) DEFAULT NULL COMMENT '商品图片(冗余)',
    `price` DECIMAL(10,2) NOT NULL COMMENT '单价',
    `quantity` INT NOT NULL DEFAULT 1 COMMENT '数量',
    `subtotal` DECIMAL(10,2) NOT NULL COMMENT '小计',
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='订单明细表';

-- ----------------------------
-- 11. 评价表
-- ----------------------------
DROP TABLE IF EXISTS `reviews`;
CREATE TABLE `reviews` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `product_id` BIGINT NOT NULL COMMENT '商品ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `rating` TINYINT NOT NULL DEFAULT 5 COMMENT '评分1-5',
    `content` TEXT COMMENT '评价内容',
    `images` TEXT COMMENT '评价图片(JSON)',
    `reply` TEXT COMMENT '商家回复',
    `reply_time` DATETIME DEFAULT NULL COMMENT '回复时间',
    `status` TINYINT DEFAULT 1 COMMENT '状态 0隐藏 1显示',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_product` (`product_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='评价表';

-- ----------------------------
-- 12. 预测结果表 (Python写入)
-- ----------------------------
DROP TABLE IF EXISTS `forecast_results`;
CREATE TABLE `forecast_results` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `flower_id` BIGINT DEFAULT NULL COMMENT '花材ID',
    `product_id` BIGINT DEFAULT NULL COMMENT '商品ID',
    `forecast_date` DATE NOT NULL COMMENT '预测日期',
    `predicted_qty` DECIMAL(10,2) NOT NULL COMMENT '预测销量',
    `lower_bound` DECIMAL(10,2) DEFAULT NULL COMMENT '预测下界',
    `upper_bound` DECIMAL(10,2) DEFAULT NULL COMMENT '预测上界',
    `model_type` VARCHAR(50) DEFAULT 'PROPHET' COMMENT '模型类型',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_flower` (`flower_id`),
    KEY `idx_date` (`forecast_date`)
) ENGINE=InnoDB COMMENT='销量预测结果表';

-- ----------------------------
-- 13. 补货建议表
-- ----------------------------
DROP TABLE IF EXISTS `restock_suggestions`;
CREATE TABLE `restock_suggestions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `flower_id` BIGINT NOT NULL COMMENT '花材ID',
    `merchant_id` BIGINT DEFAULT NULL COMMENT '商家ID',
    `current_stock` INT NOT NULL COMMENT '当前库存',
    `predicted_demand` INT NOT NULL COMMENT '预测需求量',
    `safety_stock` INT NOT NULL COMMENT '安全库存',
    `reorder_point` INT NOT NULL COMMENT '再订货点',
    `suggested_qty` INT NOT NULL COMMENT '建议补货量',
    `urgency` VARCHAR(10) DEFAULT 'NORMAL' COMMENT 'URGENT紧急/NORMAL一般/LOW低',
    `status` VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING待处理/ACCEPTED已采纳/IGNORED已忽略',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_flower` (`flower_id`),
    KEY `idx_merchant` (`merchant_id`)
) ENGINE=InnoDB COMMENT='补货建议表';

-- ----------------------------
-- 14. 推荐结果表
-- ----------------------------
DROP TABLE IF EXISTS `recommend_results`;
CREATE TABLE `recommend_results` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `product_id` BIGINT NOT NULL COMMENT '推荐商品ID',
    `score` DECIMAL(5,4) DEFAULT NULL COMMENT '推荐分数',
    `reason` VARCHAR(200) DEFAULT NULL COMMENT '推荐理由',
    `recommend_type` VARCHAR(30) DEFAULT 'HYBRID' COMMENT 'HOT热销/PREFERENCE偏好/SIMILAR相似/HYBRID混合',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='推荐结果表';

-- ----------------------------
-- 15. 系统配置表
-- ----------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(300) DEFAULT NULL COMMENT '说明',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB COMMENT='系统配置表';

-- ----------------------------
-- 16. 操作日志表
-- ----------------------------
DROP TABLE IF EXISTS `operation_logs`;
CREATE TABLE `operation_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
    `module` VARCHAR(50) DEFAULT NULL COMMENT '模块',
    `action` VARCHAR(50) DEFAULT NULL COMMENT '操作',
    `detail` TEXT COMMENT '详情',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_user` (`user_id`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='操作日志表';

-- ============================================
-- 初始数据
-- ============================================

-- 管理员账号
INSERT INTO `users` (`username`, `password`, `nickname`, `role`, `status`) VALUES
('admin', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 'ADMIN', 1);

-- 商家账号
INSERT INTO `users` (`username`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
('merchant1', 'e10adc3949ba59abbe56e057f20f883e', '花语心愿店主', '13800138001', 'MERCHANT', 1);

-- 测试用户
INSERT INTO `users` (`username`, `password`, `nickname`, `phone`, `role`, `status`) VALUES
('user1', 'e10adc3949ba59abbe56e057f20f883e', '小花', '13900139001', 'USER', 1),
('user2', 'e10adc3949ba59abbe56e057f20f883e', '花花', '13900139002', 'USER', 1);

-- 商家信息
INSERT INTO `merchants` (`user_id`, `shop_name`, `contact_name`, `contact_phone`, `address`, `description`, `status`) VALUES
(2, '花语心愿', '张花匠', '13800138001', '北京市朝阳区花卉大道88号', '专注高品质鲜花，让每一束花都传递心意', 1);

-- 分类
INSERT INTO `categories` (`name`, `icon`, `sort_order`) VALUES
('玫瑰花束', '🌹', 1),
('百合花束', '💐', 2),
('向日葵', '🌻', 3),
('康乃馨', '🌸', 4),
('混搭花束', '💮', 5),
('永生花', '🌺', 6),
('绿植盆栽', '🌿', 7),
('花材单品', '🌼', 8);

-- 花材
INSERT INTO `flowers` (`name`, `category`, `unit`, `cost_price`, `sell_price`, `shelf_life_days`, `alert_threshold`, `merchant_id`) VALUES
('红玫瑰', '玫瑰', '枝', 3.00, 8.00, 7, 50, 1),
('粉玫瑰', '玫瑰', '枝', 3.50, 8.00, 7, 50, 1),
('白玫瑰', '玫瑰', '枝', 3.00, 8.00, 7, 40, 1),
('香槟玫瑰', '玫瑰', '枝', 4.00, 10.00, 6, 30, 1),
('白百合', '百合', '枝', 6.00, 15.00, 10, 20, 1),
('粉百合', '百合', '枝', 6.50, 15.00, 10, 20, 1),
('向日葵', '向日葵', '枝', 4.00, 12.00, 5, 30, 1),
('康乃馨(红)', '康乃馨', '枝', 2.00, 5.00, 10, 60, 1),
('康乃馨(粉)', '康乃馨', '枝', 2.00, 5.00, 10, 60, 1),
('满天星', '配花', '扎', 5.00, 12.00, 14, 20, 1),
('尤加利叶', '配叶', '枝', 1.50, 4.00, 14, 30, 1),
('雏菊', '菊花', '枝', 1.50, 4.00, 7, 40, 1),
('桔梗', '配花', '枝', 2.50, 6.00, 7, 30, 1),
('勿忘我', '配花', '扎', 4.00, 10.00, 14, 20, 1),
('牛皮纸包装', '包材', '张', 2.00, 5.00, 365, 100, 1),
('雾面纸包装', '包材', '张', 3.00, 8.00, 365, 100, 1),
('丝带', '包材', '米', 0.50, 2.00, 365, 200, 1);

-- 商品
INSERT INTO `products` (`title`, `subtitle`, `category_id`, `merchant_id`, `price`, `original_price`, `cost_price`, `cover_image`, `description`, `product_type`, `sales_count`, `stock`, `packaging_fee`, `delivery_fee`, `is_recommend`) VALUES
('热恋红玫瑰花束', '11枝红玫瑰+满天星 | 经典浪漫', 1, 1, 128.00, 168.00, 52.00, 'https://images.unsplash.com/photo-1563436798111-3763e8457811?auto=format&fit=crop&w=1200&q=80', '精选11枝A级红玫瑰，搭配满天星和尤加利叶，雾面纸精美包装，传递最炽热的爱意', 'BUNDLE', 356, 99, 15.00, 10.00, 1),
('粉色恋曲花束', '19枝粉玫瑰 | 甜蜜告白', 1, 1, 199.00, 258.00, 86.50, 'https://images.unsplash.com/photo-1533793241176-a270e75ef2ad?auto=format&fit=crop&w=1200&q=80', '19枝精选粉玫瑰，搭配桔梗和满天星，牛皮纸复古包装，诉说粉色少女心', 'BUNDLE', 218, 99, 15.00, 10.00, 1),
('纯白誓言花束', '33枝白玫瑰 | 纯洁永恒', 1, 1, 299.00, 399.00, 128.00, 'https://images.unsplash.com/photo-1609840533612-0cdfb9418281?auto=format&fit=crop&w=1200&q=80', '33枝白玫瑰象征三生三世，搭配尤加利叶，简约大气', 'BUNDLE', 145, 99, 20.00, 10.00, 1),
('温馨康乃馨花束', '19枝康乃馨+百合 | 感恩之花', 4, 1, 158.00, 198.00, 63.00, 'https://images.unsplash.com/photo-1741637723809-64f54d36bcc6?auto=format&fit=crop&w=1200&q=80', '19枝粉色康乃馨搭配2枝白百合，表达对母亲最深的感恩', 'BUNDLE', 189, 99, 15.00, 10.00, 1),
('阳光向日葵花束', '6枝向日葵 | 活力满满', 3, 1, 138.00, 178.00, 42.00, 'https://images.unsplash.com/photo-1594797075747-1e99f592b285?auto=format&fit=crop&w=1200&q=80', '6枝向日葵搭配雏菊和尤加利叶，如同一束阳光照进生活', 'BUNDLE', 267, 99, 15.00, 10.00, 1),
('浪漫满天星花束', '一大扎满天星 | 如繁星点点', 5, 1, 89.00, 128.00, 25.00, 'https://images.unsplash.com/photo-1741637723809-64f54d36bcc6?auto=format&fit=crop&w=1200&q=80', '纯白满天星，如夜空中闪烁的繁星，纯净而浪漫', 'BUNDLE', 412, 99, 10.00, 10.00, 1),
('香槟之恋花束', '11枝香槟玫瑰 | 优雅迷人', 1, 1, 158.00, 208.00, 65.00, 'https://images.unsplash.com/photo-1583086804996-424d089537cb?auto=format&fit=crop&w=1200&q=80', '11枝香槟玫瑰搭配桔梗和勿忘我，展现成熟优雅的魅力', 'BUNDLE', 156, 99, 15.00, 10.00, 1),
('红玫瑰 (单枝)', '高品质A级红玫瑰', 8, 1, 8.00, NULL, 3.00, 'https://images.unsplash.com/photo-1684826120615-09297bfa5495?auto=format&fit=crop&w=1200&q=80', '单枝A级红玫瑰，花头饱满，色泽鲜艳', 'SINGLE', 89, 200, 0.00, 5.00, 0);

-- BOM (物料清单)
-- 热恋红玫瑰花束 = 11枝红玫瑰 + 1扎满天星 + 3枝尤加利叶 + 1张雾面纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(1, 1, 11), (1, 10, 1), (1, 11, 3), (1, 16, 1), (1, 17, 1);

-- 粉色恋曲花束 = 19枝粉玫瑰 + 3枝桔梗 + 1扎满天星 + 1张牛皮纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(2, 2, 19), (2, 13, 3), (2, 10, 1), (2, 15, 1), (2, 17, 1);

-- 纯白誓言花束 = 33枝白玫瑰 + 5枝尤加利叶 + 1张雾面纸 + 2米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(3, 3, 33), (3, 11, 5), (3, 16, 1), (3, 17, 2);

-- 温馨康乃馨花束 = 19枝粉康乃馨 + 2枝白百合 + 1张雾面纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(4, 9, 19), (4, 5, 2), (4, 16, 1), (4, 17, 1);

-- 阳光向日葵花束 = 6枝向日葵 + 5枝雏菊 + 3枝尤加利叶 + 1张牛皮纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(5, 7, 6), (5, 12, 5), (5, 11, 3), (5, 15, 1), (5, 17, 1);

-- 浪漫满天星花束 = 3扎满天星 + 1张雾面纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(6, 10, 3), (6, 16, 1), (6, 17, 1);

-- 香槟之恋花束 = 11枝香槟玫瑰 + 3枝桔梗 + 1扎勿忘我 + 1张雾面纸 + 1米丝带
INSERT INTO `product_bom` (`product_id`, `flower_id`, `quantity`) VALUES
(7, 4, 11), (7, 13, 3), (7, 14, 1), (7, 16, 1), (7, 17, 1);

-- 库存批次数据
INSERT INTO `inventory_batches` (`flower_id`, `merchant_id`, `supplier_name`, `receipt_date`, `wilt_date`, `original_qty`, `current_qty`, `cost_per_unit`, `quality_status`) VALUES
(1, 1, '云南花卉批发市场', '2026-02-08 08:00:00', '2026-02-15 08:00:00', 200, 150, 3.00, 'A'),
(2, 1, '云南花卉批发市场', '2026-02-08 08:00:00', '2026-02-15 08:00:00', 150, 120, 3.50, 'A'),
(3, 1, '云南花卉批发市场', '2026-02-08 08:00:00', '2026-02-15 08:00:00', 100, 80, 3.00, 'A'),
(4, 1, '进口花材中心', '2026-02-09 08:00:00', '2026-02-15 08:00:00', 80, 65, 4.00, 'A'),
(5, 1, '云南花卉批发市场', '2026-02-07 08:00:00', '2026-02-17 08:00:00', 60, 40, 6.00, 'A'),
(7, 1, '云南花卉批发市场', '2026-02-08 08:00:00', '2026-02-13 08:00:00', 100, 70, 4.00, 'A'),
(8, 1, '本地花农直供', '2026-02-06 08:00:00', '2026-02-16 08:00:00', 200, 140, 2.00, 'A'),
(9, 1, '本地花农直供', '2026-02-06 08:00:00', '2026-02-16 08:00:00', 200, 160, 2.00, 'A'),
(10, 1, '云南花卉批发市场', '2026-02-05 08:00:00', '2026-02-19 08:00:00', 50, 30, 5.00, 'A'),
(11, 1, '本地花农直供', '2026-02-07 08:00:00', '2026-02-21 08:00:00', 100, 75, 1.50, 'A'),
(12, 1, '本地花农直供', '2026-02-08 08:00:00', '2026-02-15 08:00:00', 150, 120, 1.50, 'A'),
(15, 1, '包材供应商', '2026-02-01 08:00:00', '2027-02-01 08:00:00', 500, 420, 2.00, 'A'),
(16, 1, '包材供应商', '2026-02-01 08:00:00', '2027-02-01 08:00:00', 500, 380, 3.00, 'A'),
(17, 1, '包材供应商', '2026-02-01 08:00:00', '2027-02-01 08:00:00', 1000, 850, 0.50, 'A');

-- 系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('default_delivery_fee', '10.00', '默认配送费'),
('free_delivery_threshold', '200.00', '免配送费门槛'),
('points_per_yuan', '1', '每消费1元获得积分'),
('inventory_scan_interval', '3600', '库存扫描间隔(秒)'),
('wilt_warning_days', '2', '枯萎预警天数'),
('site_name', '花语心愿', '站点名称');
