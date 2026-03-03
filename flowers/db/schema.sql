CREATE DATABASE IF NOT EXISTS flower_shop DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE flower_shop;
SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS banner;
DROP TABLE IF EXISTS system_config;
DROP TABLE IF EXISTS user_favorite;
DROP TABLE IF EXISTS user_address;
DROP TABLE IF EXISTS cart_item;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS merchant;
DROP TABLE IF EXISTS recommendation_result;
DROP TABLE IF EXISTS replenishment_suggestion;
DROP TABLE IF EXISTS forecast_result;
DROP TABLE IF EXISTS review;
DROP TABLE IF EXISTS stock_lock;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS customer_order;
DROP TABLE IF EXISTS inventory_batch;
DROP TABLE IF EXISTS product_bom;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS flower_material;
DROP TABLE IF EXISTS user_customer;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE user_customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  phone VARCHAR(20) NULL UNIQUE,
  points INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE user_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  receiver_name VARCHAR(64) NOT NULL,
  receiver_phone VARCHAR(20) NOT NULL,
  province VARCHAR(64) NOT NULL DEFAULT '',
  city VARCHAR(64) NOT NULL DEFAULT '',
  district VARCHAR(64) NOT NULL DEFAULT '',
  detail VARCHAR(255) NOT NULL,
  is_default TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_address_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  INDEX idx_user_address_user (user_id),
  INDEX idx_user_address_default (user_id, is_default)
);

CREATE TABLE flower_material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  category VARCHAR(32) NOT NULL,
  unit VARCHAR(16) NOT NULL DEFAULT 'stem',
  sale_price DECIMAL(10,2) NOT NULL,
  cost_price DECIMAL(10,2) NOT NULL,
  shelf_life_days INT NOT NULL DEFAULT 7,
  warn_threshold DECIMAL(10,2) NOT NULL DEFAULT 10.00,
  image_url VARCHAR(255) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_flower_category (category),
  INDEX idx_flower_enabled (enabled)
);

CREATE TABLE product (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(128) NOT NULL,
  type VARCHAR(16) NOT NULL COMMENT 'SINGLE/BOUQUET/CUSTOM',
  category VARCHAR(32) NOT NULL,
  base_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  packaging_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  delivery_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  description VARCHAR(1024) NULL,
  cover_image VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE/OFF_SHELF',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_product_status (status),
  INDEX idx_product_category (category)
);

CREATE TABLE user_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_favorite (user_id, product_id),
  CONSTRAINT fk_user_favorite_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  CONSTRAINT fk_user_favorite_product FOREIGN KEY (product_id) REFERENCES product(id),
  INDEX idx_user_favorite_user (user_id),
  INDEX idx_user_favorite_product (product_id)
);

CREATE TABLE product_bom (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  flower_id BIGINT NOT NULL,
  dosage DECIMAL(10,2) NOT NULL COMMENT '单件商品所需花材用量',
  loss_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_product_flower (product_id, flower_id),
  CONSTRAINT fk_bom_product FOREIGN KEY (product_id) REFERENCES product(id),
  CONSTRAINT fk_bom_flower FOREIGN KEY (flower_id) REFERENCES flower_material(id)
);

CREATE TABLE inventory_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flower_id BIGINT NOT NULL,
  supplier_name VARCHAR(128) NOT NULL,
  receipt_time DATETIME NOT NULL,
  wilt_time DATETIME NOT NULL,
  quality_status VARCHAR(8) NOT NULL DEFAULT 'A' COMMENT 'A/B/C',
  original_qty DECIMAL(10,2) NOT NULL,
  current_qty DECIMAL(10,2) NOT NULL,
  locked_qty DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  unit_cost DECIMAL(10,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_batch_flower FOREIGN KEY (flower_id) REFERENCES flower_material(id),
  INDEX idx_batch_flower (flower_id),
  INDEX idx_batch_fefo (flower_id, wilt_time, receipt_time)
);

CREATE TABLE customer_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  payment_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(20) NOT NULL COMMENT 'CREATED/LOCKED/PAID/CONFIRMED/CANCELLED/REFUNDED/COMPLETED',
  receiver_name VARCHAR(64) NULL,
  receiver_phone VARCHAR(20) NULL,
  receiver_address VARCHAR(255) NULL,
  payment_channel VARCHAR(32) NULL,
  payment_no VARCHAR(64) NULL,
  pay_time DATETIME NULL,
  cancel_time DATETIME NULL,
  lock_expire_at DATETIME NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  INDEX idx_order_status (status),
  INDEX idx_order_user (user_id),
  INDEX idx_order_created_at (created_at)
);

CREATE TABLE order_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_title VARCHAR(128) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  line_amount DECIMAL(12,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product(id),
  INDEX idx_item_order (order_id)
);

CREATE TABLE stock_lock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40) NOT NULL,
  flower_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  lock_qty DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'LOCKED/CONFIRMED/RELEASED/ROLLED_BACK',
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_lock_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_lock_flower FOREIGN KEY (flower_id) REFERENCES flower_material(id),
  CONSTRAINT fk_lock_batch FOREIGN KEY (batch_id) REFERENCES inventory_batch(id),
  INDEX idx_lock_order (order_id),
  INDEX idx_lock_order_no (order_no),
  INDEX idx_lock_status (status),
  INDEX idx_lock_expires (expires_at)
);

CREATE TABLE review (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  score TINYINT NOT NULL,
  content VARCHAR(1024) NULL,
  tags VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  INDEX idx_review_user (user_id)
);

CREATE TABLE forecast_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flower_id BIGINT NOT NULL,
  forecast_date DATE NOT NULL,
  predicted_sales DECIMAL(10,2) NOT NULL,
  confidence_lower DECIMAL(10,2) NULL,
  confidence_upper DECIMAL(10,2) NULL,
  model_name VARCHAR(32) NOT NULL,
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_forecast_flower FOREIGN KEY (flower_id) REFERENCES flower_material(id),
  UNIQUE KEY uk_forecast_flower_day (flower_id, forecast_date),
  INDEX idx_forecast_date (forecast_date)
);

CREATE TABLE replenishment_suggestion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flower_id BIGINT NOT NULL,
  suggestion_date DATE NOT NULL,
  predicted_demand DECIMAL(10,2) NOT NULL,
  safety_stock DECIMAL(10,2) NOT NULL,
  reorder_point DECIMAL(10,2) NOT NULL,
  on_hand DECIMAL(10,2) NOT NULL,
  in_transit DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  suggested_qty DECIMAL(10,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/CONFIRMED/IGNORED',
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_suggestion_flower FOREIGN KEY (flower_id) REFERENCES flower_material(id),
  UNIQUE KEY uk_suggestion_flower_day (flower_id, suggestion_date)
);

CREATE TABLE recommendation_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  score DECIMAL(10,4) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_recommend_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  CONSTRAINT fk_recommend_product FOREIGN KEY (product_id) REFERENCES product(id),
  INDEX idx_recommend_user (user_id),
  INDEX idx_recommend_generated (generated_at)
);

CREATE TABLE merchant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  contact_phone VARCHAR(20) NULL,
  email VARCHAR(128) NULL,
  address VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_merchant_status (status)
);

CREATE TABLE category (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE COMMENT '英文编码，与 product.category 对应',
  name VARCHAR(32) NOT NULL UNIQUE,
  sort_order INT NOT NULL DEFAULT 0,
  icon VARCHAR(255) NULL,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE cart_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_product (user_id, product_id),
  CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES user_customer(id),
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES product(id),
  INDEX idx_cart_user (user_id)
);

CREATE TABLE system_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  config_key VARCHAR(64) NOT NULL UNIQUE,
  config_value VARCHAR(512) NOT NULL,
  description VARCHAR(255) NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE banner (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(64) NOT NULL,
  subtitle VARCHAR(128) NULL,
  color_from VARCHAR(16) NOT NULL DEFAULT '#FF6B9D',
  color_to VARCHAR(16) NOT NULL DEFAULT '#E91E63',
  link_url VARCHAR(255) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE payment_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40) NOT NULL,
  transaction_id VARCHAR(64) NOT NULL COMMENT '微信支付订单号',
  payment_channel VARCHAR(32) NOT NULL DEFAULT 'WECHAT_PAY' COMMENT '支付渠道',
  pay_amount DECIMAL(12,2) NOT NULL,
  result_code VARCHAR(32) NOT NULL,
  notify_time DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES customer_order(id),
  INDEX idx_payment_order (order_id),
  INDEX idx_payment_order_no (order_no),
  INDEX idx_payment_transaction (transaction_id),
  INDEX idx_payment_created (created_at)
);
