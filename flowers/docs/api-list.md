# 接口清单（按模块分组）

## 1. 商品模块

- `GET /api/v1/products`：商品列表（可按 `category` 过滤）
- `GET /api/v1/products/{productId}`：商品详情（含 BOM 与自动计价结果）
- `GET /api/v1/products/recommend/recent`：按近期销量推荐花束（参数：`days`、`limit`）

## 2. 订单模块

- `POST /api/v1/orders`：创建订单并锁库存
  - 支持传入 `deliveryFee` 与配送信息备注（用于同城配送/急送/定时场景）
- `GET /api/v1/orders/{orderNo}`：订单详情
- `GET /api/v1/orders/user/{userId}`：用户订单号列表
- `GET /api/v1/orders/user/{userId}/details`：用户订单详情列表（用于前端订单页一次性加载）
- `POST /api/v1/orders/{orderNo}/pay`：支付成功确认并正式扣减库存
- `POST /api/v1/orders/{orderNo}/cancel`：取消/退款并回滚库存
- `POST /api/v1/orders/release-expired`：批量释放超时未支付订单锁库存

## 3. 库存模块

- `GET /api/v1/inventory/alerts`：低库存预警列表
- `GET /api/v1/inventory/fefo/{flowerId}`：按 FEFO 顺序查看批次可用库存

## 4. 分析结果查询模块（给商家后台）

- `GET /api/v1/analysis/replenishment`：补货建议（默认当天）
- `GET /api/v1/analysis/recommendations`：用户推荐结果查询

## 5. 管理端基础数据模块

- `GET /api/v1/merchants`：商家列表
- `GET /api/v1/system-config`：系统配置列表

## 6. 调试造数模块（本地开发）

- `POST /api/v1/debug/seed-visual-data`：补充图片字段并生成近期销量样本数据（参数：`orderCount`）

## 7. Python 分析服务接口

- `GET /health`：健康检查
- `POST /forecast/run`：执行销量预测并落库
- `POST /recommendation/run`：生成推荐结果并落库

## 8. 典型请求示例

### 8.1 创建订单

```http
POST /api/v1/orders
Content-Type: application/json

{
  "userId": 1,
  "items": [
    {"productId": 1, "quantity": 1},
    {"productId": 2, "quantity": 1}
  ],
  "receiverName": "林小雨",
  "receiverPhone": "13800000001",
  "receiverAddress": "上海市静安区南京西路100号",
  "packagingFee": 2.00,
  "deliveryFee": 6.00,
  "remark": "晚间配送"
}
```

### 8.2 支付确认

```http
POST /api/v1/orders/FO202602101230300001/pay
Content-Type: application/json

{
  "paymentChannel": "WECHAT_PAY",
  "paymentNo": "wxpay_123456"
}
```
