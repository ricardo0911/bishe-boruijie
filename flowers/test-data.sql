USE flower_shop;

INSERT INTO user_customer (openid, name, phone, points)
VALUES
('wx_u_1001', '林小雨', '13800000001', 120),
('wx_u_1002', '陈子墨', '13800000002', 80),
('wx_u_1003', '赵一宁', '13800000003', 40);

INSERT INTO flower_material (name, category, unit, sale_price, cost_price, shelf_life_days, warn_threshold, image_url)
VALUES
('红玫瑰', 'ROSE', 'stem', 8.00, 5.00, 7, 30, '/images/materials/rose.svg'),
('满天星', 'ACCESSORY', 'stem', 2.50, 1.20, 10, 20, '/images/materials/gypsophila.svg'),
('白百合', 'LILY', 'stem', 10.00, 6.50, 8, 15, '/images/materials/lily.svg'),
('康乃馨', 'CARNATION', 'stem', 4.50, 2.50, 9, 25, '/images/materials/carnation.svg'),
('包装纸', 'PACKAGING', 'sheet', 1.20, 0.50, 365, 50, '/images/materials/wrap.svg'),
('丝带', 'PACKAGING', 'meter', 0.80, 0.30, 365, 30, '/images/materials/ribbon.svg');

INSERT INTO product (title, type, category, base_price, packaging_fee, delivery_fee, description, cover_image, status)
VALUES
('浪漫11枝红玫瑰', 'BOUQUET', 'VALENTINE', 0.00, 6.00, 8.00, '经典告白款，适用于纪念日和节日。', '/images/products/custom/rose-black-baccara.jpg', 'ON_SALE'),
('清新百合花束', 'BOUQUET', 'DAILY', 0.00, 5.00, 8.00, '适合日常送礼，风格简约。', '/images/products/custom/rose-amour.jpg', 'ON_SALE'),
('康乃馨关怀款', 'BOUQUET', 'MOTHER_DAY', 0.00, 4.00, 8.00, '母亲节与探访场景热销。', '/images/products/custom/rose-zhenai.jpg', 'ON_SALE'),
('法式香槟玫瑰花束', 'BOUQUET', 'DAILY', 0.00, 6.00, 8.00, '法式配色，适合约会与纪念日场景。', '/images/products/custom/rose-explorer.jpg', 'ON_SALE'),
('向日葵元气花束', 'BOUQUET', 'BIRTHDAY', 0.00, 5.00, 8.00, '明亮活力，适合生日祝福与开业庆贺。', '/images/products/custom/rose-freedom.jpg', 'ON_SALE'),
('白绿商务花束', 'BOUQUET', 'BUSINESS', 0.00, 7.00, 8.00, '商务拜访与会议场景优选。', '/images/products/custom/rose-lisi.jpg', 'ON_SALE'),
('粉色告白花束', 'BOUQUET', 'VALENTINE', 0.00, 6.00, 8.00, '粉系浪漫组合，适合告白与周年纪念。', '/images/products/custom/rose-baoliandeng.jpg', 'ON_SALE'),
('暖心康乃馨礼盒', 'BOUQUET', 'MOTHER_DAY', 0.00, 4.00, 8.00, '关怀主题，适合探望与节日赠礼。', '/images/products/custom/rose-carola.jpg', 'ON_SALE');

INSERT INTO product_bom (product_id, flower_id, dosage, loss_rate)
VALUES
-- 浪漫11枝红玫瑰
(1, 1, 11, 0.03),
(1, 2, 3, 0.05),
(1, 5, 2, 0.00),
(1, 6, 1, 0.00),
-- 清新百合花束
(2, 3, 6, 0.03),
(2, 2, 2, 0.05),
(2, 5, 2, 0.00),
(2, 6, 1, 0.00),
-- 康乃馨关怀款
(3, 4, 12, 0.03),
(3, 2, 2, 0.05),
(3, 5, 2, 0.00),
(3, 6, 1, 0.00),
-- 法式香槟玫瑰花束
(4, 1, 9, 0.03),
(4, 2, 4, 0.05),
(4, 5, 2, 0.00),
(4, 6, 2, 0.00),
-- 向日葵元气花束（使用现有花材组合模拟）
(5, 3, 5, 0.03),
(5, 4, 4, 0.03),
(5, 2, 2, 0.05),
(5, 5, 2, 0.00),
(5, 6, 1, 0.00),
-- 白绿商务花束
(6, 3, 4, 0.03),
(6, 1, 6, 0.03),
(6, 5, 3, 0.00),
(6, 6, 1, 0.00),
-- 粉色告白花束
(7, 1, 7, 0.03),
(7, 2, 5, 0.05),
(7, 5, 2, 0.00),
(7, 6, 1, 0.00),
-- 暖心康乃馨礼盒
(8, 4, 15, 0.03),
(8, 2, 4, 0.05),
(8, 5, 3, 0.00),
(8, 6, 2, 0.00);

INSERT INTO inventory_batch (flower_id, supplier_name, receipt_time, wilt_time, quality_status, original_qty, current_qty, locked_qty, unit_cost)
VALUES
-- 红玫瑰批次
(1, '昆明花材供应A', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 5 DAY, 'A', 200, 180, 0, 5.00),
(1, '昆明花材供应A', NOW() - INTERVAL 3 DAY, NOW() + INTERVAL 3 DAY, 'B', 120, 90, 0, 4.80),
-- 满天星批次
(2, '云南配花供应B', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 7 DAY, 'A', 160, 150, 0, 1.20),
-- 白百合批次
(3, '昆明花材供应A', NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 6 DAY, 'A', 100, 90, 0, 6.50),
-- 康乃馨批次
(4, '华南花材供应C', NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 6 DAY, 'A', 180, 160, 0, 2.50),
-- 包装纸批次
(5, '包装材料商D', NOW() - INTERVAL 5 DAY, NOW() + INTERVAL 360 DAY, 'A', 1000, 900, 0, 0.50),
-- 丝带批次
(6, '包装材料商D', NOW() - INTERVAL 5 DAY, NOW() + INTERVAL 360 DAY, 'A', 800, 750, 0, 0.30);

INSERT INTO forecast_result (flower_id, forecast_date, predicted_sales, confidence_lower, confidence_upper, model_name)
VALUES
(1, CURDATE() + INTERVAL 1 DAY, 28.00, 20.00, 36.00, 'moving_average'),
(2, CURDATE() + INTERVAL 1 DAY, 10.00, 6.00, 15.00, 'moving_average'),
(3, CURDATE() + INTERVAL 1 DAY, 9.00, 5.00, 14.00, 'moving_average'),
(4, CURDATE() + INTERVAL 1 DAY, 16.00, 10.00, 23.00, 'moving_average');

INSERT INTO replenishment_suggestion (flower_id, suggestion_date, predicted_demand, safety_stock, reorder_point, on_hand, in_transit, suggested_qty, status)
VALUES
(1, CURDATE(), 84.00, 15.00, 45.00, 270.00, 0.00, 0.00, 'NEW'),
(2, CURDATE(), 30.00, 8.00, 18.00, 150.00, 0.00, 0.00, 'NEW'),
(3, CURDATE(), 27.00, 6.00, 15.00, 90.00, 0.00, 0.00, 'NEW'),
(4, CURDATE(), 48.00, 10.00, 28.00, 160.00, 0.00, 0.00, 'NEW');

INSERT INTO recommendation_result (user_id, product_id, score, reason)
VALUES
(1, 2, 0.9100, '偏好清新风格，且近期百合花束库存充足'),
(2, 1, 0.9600, '节日偏好与历史购买匹配，玫瑰热销'),
(3, 3, 0.8800, '商务与关怀场景匹配，康乃馨成交率高');

INSERT INTO merchant (name, contact_phone, email, address, status)
VALUES
('花语轩旗舰店', '13900000001', 'huayuxuan@flower.com', '上海市静安区南京西路100号', 'ACTIVE'),
('春风花艺', '13900000002', 'chunfeng@flower.com', '北京市朝阳区三里屯路88号', 'ACTIVE');

INSERT INTO category (code, name, sort_order, icon, enabled)
VALUES
('VALENTINE', '情人节', 1, '/icons/valentine.png', 1),
('DAILY', '日常鲜花', 2, '/icons/daily.png', 1),
('MOTHER_DAY', '母亲节', 3, '/icons/mother.png', 1),
('BUSINESS', '商务用花', 4, '/icons/business.png', 1),
('BIRTHDAY', '生日祝福', 5, '/icons/birthday.png', 1);

INSERT INTO cart_item (user_id, product_id, quantity)
VALUES
(1, 1, 2),
(1, 2, 1),
(2, 3, 1);

INSERT INTO system_config (config_key, config_value, description)
VALUES
('order_lock_minutes', '30', '订单锁库存超时时间（分钟）'),
('delivery_fee_default', '8.00', '默认配送费'),
('points_per_yuan', '1', '每消费1元获得积分数'),
('low_stock_scan_cron', '0 0 8 * * ?', '库存预警扫描cron表达式');

INSERT INTO banner (title, subtitle, color_from, color_to, link_url, sort_order, enabled)
VALUES
('春日花语 浪漫绽放', '全场鲜花 新人专享8折', '#FF6B9D', '#E91E63', 'category.html', 1, 1),
('母亲节特惠', '感恩花束 温馨献礼', '#7C4DFF', '#536DFE', 'category.html?category=MOTHER_DAY', 2, 1),
('每日鲜花 准时送达', '同城2小时极速配送', '#FF9800', '#F57C00', 'category.html', 3, 1);

INSERT INTO customer_order (
  order_no, user_id, total_amount, payment_amount, status, payment_channel, payment_no,
  pay_time, cancel_time, lock_expire_at, remark, created_at, updated_at
)
VALUES
('FO202602010001', 1, 225.40, 225.40, 'PAID', 'MOCK_WECHAT', 'MOCK_202602010001', NOW() - INTERVAL 9 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY),
('FO202602020001', 2, 200.00, 200.00, 'COMPLETED', 'MOCK_WECHAT', 'MOCK_202602020001', NOW() - INTERVAL 8 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 8 DAY),
('FO202602030001', 3, 81.20, 81.20, 'PAID', 'MOCK_WECHAT', 'MOCK_202602030001', NOW() - INTERVAL 7 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 7 DAY),
('FO202602040001', 1, 267.60, 267.60, 'COMPLETED', 'MOCK_WECHAT', 'MOCK_202602040001', NOW() - INTERVAL 6 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY),
('FO202602050001', 2, 212.70, 212.70, 'PAID', 'MOCK_WECHAT', 'MOCK_202602050001', NOW() - INTERVAL 5 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
('FO202602060001', 3, 214.80, 214.80, 'PAID', 'MOCK_WECHAT', 'MOCK_202602060001', NOW() - INTERVAL 4 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
('FO202602070001', 1, 171.40, 171.40, 'PAID', 'MOCK_WECHAT', 'MOCK_202602070001', NOW() - INTERVAL 3 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),
('FO202602080001', 2, 261.10, 261.10, 'COMPLETED', 'MOCK_WECHAT', 'MOCK_202602080001', NOW() - INTERVAL 2 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),
('FO202602090001', 3, 94.70, 94.70, 'PAID', 'MOCK_WECHAT', 'MOCK_202602090001', NOW() - INTERVAL 1 DAY, NULL, NULL, 'seed 近期销量样本', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
('FO202602100001', 1, 100.00, 100.00, 'LOCKED', NULL, NULL, NULL, NULL, NOW() + INTERVAL 20 MINUTE, 'seed 待支付样本', NOW() - INTERVAL 20 MINUTE, NOW() - INTERVAL 20 MINUTE),
('FO202602110001', 2, 81.20, 0.00, 'CANCELLED', NULL, NULL, NULL, NOW() - INTERVAL 12 HOUR, NULL, 'seed 已取消样本', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 12 HOUR),
('FO202602120001', 3, 112.70, 112.70, 'REFUNDED', 'MOCK_WECHAT', 'MOCK_202602120001', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 6 HOUR, NULL, 'seed 已退款样本', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 6 HOUR);

INSERT INTO order_item (order_id, product_id, product_title, unit_price, quantity, line_amount, created_at, updated_at)
VALUES
((SELECT id FROM customer_order WHERE order_no = 'FO202602010001'), 1, '浪漫11枝红玫瑰', 112.70, 2, 225.40, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 9 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602020001'), 4, '法式香槟玫瑰花束', 100.00, 2, 200.00, NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 8 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602030001'), 2, '清新百合花束', 81.20, 1, 81.20, NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 7 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602040001'), 5, '向日葵元气花束', 89.20, 3, 267.60, NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 6 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602050001'), 1, '浪漫11枝红玫瑰', 112.70, 1, 112.70, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602050001'), 4, '法式香槟玫瑰花束', 100.00, 1, 100.00, NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602060001'), 6, '白绿商务花束', 107.40, 2, 214.80, NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602070001'), 7, '粉色告白花束', 85.70, 2, 171.40, NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 3 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602080001'), 3, '康乃馨关怀款', 74.20, 2, 148.40, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602080001'), 1, '浪漫11枝红玫瑰', 112.70, 1, 112.70, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 2 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602090001'), 8, '暖心康乃馨礼盒', 94.70, 1, 94.70, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602100001'), 4, '法式香槟玫瑰花束', 100.00, 1, 100.00, NOW() - INTERVAL 20 MINUTE, NOW() - INTERVAL 20 MINUTE),
((SELECT id FROM customer_order WHERE order_no = 'FO202602110001'), 2, '清新百合花束', 81.20, 1, 81.20, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 12 HOUR),
((SELECT id FROM customer_order WHERE order_no = 'FO202602120001'), 1, '浪漫11枝红玫瑰', 112.70, 1, 112.70, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 6 HOUR);

INSERT INTO review (order_id, user_id, score, content, tags, created_at)
VALUES
((SELECT id FROM customer_order WHERE order_no = 'FO202602010001'), 1, 5, '花很新鲜，包装也很精致。', '新鲜,包装好', NOW() - INTERVAL 8 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602020001'), 2, 5, '配送及时，颜色搭配很高级。', '配送快,高级', NOW() - INTERVAL 7 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602040001'), 1, 4, '整体不错，性价比可以。', '性价比', NOW() - INTERVAL 5 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602060001'), 3, 5, '商务送礼很合适，客户反馈很好。', '商务,推荐', NOW() - INTERVAL 3 DAY),
((SELECT id FROM customer_order WHERE order_no = 'FO202602080001'), 2, 4, '康乃馨很饱满，下次还会回购。', '回购', NOW() - INTERVAL 1 DAY);
