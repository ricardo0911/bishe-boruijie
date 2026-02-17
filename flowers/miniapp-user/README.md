# Flower Mini Program (User Side)

这是基于你现有后端接口实现的微信小程序用户端，包含：

- 首页（轮播 + 推荐 + 分类）
- 首页创意功能：送花灵感实验室（场景+预算智能推荐）+ 祝福卡文案一键复制
- 分类页（分类筛选 + 搜索）
- 商品详情（BOM 花材 + 评价）
- 购物车（增减数量 + 结算）
- 结算页（收货信息 + 同城配送方式/时段 + 下单）
- 订单页（状态筛选 + 支付 + 取消/退款）
- 个人中心（查看资料 + 编辑资料）

## 目录

```text
miniapp-user/
  app.js
  app.json
  app.wxss
  project.config.json
  sitemap.json
  utils/
  pages/
```

## 使用步骤

1. 启动后端服务（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

2. 打开微信开发者工具

- 导入目录：`miniapp-user`
- `appid` 可先用测试号（`touristappid`）

3. 开发环境设置（重要）

- 在开发者工具里关闭以下校验（仅本地开发）：
  - 不校验合法域名
  - 不校验 TLS 版本
  - 不校验 HTTPS 证书

4. API 地址

- 默认：`http://127.0.0.1:9090/api/v1`
- 如需修改，在 `app.js` 的 `globalData.apiBase` 调整
- 如果你用手机真机预览，`127.0.0.1` 不可用，请改为电脑局域网 IP（如 `http://192.168.1.10:9090/api/v1`）

## 已对齐的后端接口

- `GET /products`
- `GET /products/recommend/recent`
- `GET /products/{id}`
- `POST /debug/seed-visual-data`（本地调试可选）
- `GET /cart/{userId}`
- `POST /cart`
- `DELETE /cart/{userId}/{productId}`
- `DELETE /cart/{userId}`
- `POST /orders`
- `GET /orders/user/{userId}/details`
- `POST /orders/{orderNo}/pay`
- `POST /orders/{orderNo}/cancel`
- `GET /users/{userId}`
- `PUT /users/{userId}`
- `GET /reviews/product/{productId}`

## 说明

- 小程序端已兼容价格字段差异：`unitPrice` / `autoPrice` / `unit_price`
- 订单页与结算页已避免 N+1 请求
- 首页“为你推荐”已改为“近期销量推荐花束”逻辑（默认近30天）
- 首页/分类/详情/购物车/结算页会优先展示数据库中的图片字段（`cover_image`）
- 结算页支持同城配送（标准/同城快递/急送/定时），并自动带入配送费与配送时段到订单备注
- 登录是**假登录模式**：自动生成 `mock_openid` 并调用 `/users/login` 建用户
- 支付是**假支付模式**：订单页点击“模拟支付”后直接调用 `/orders/{orderNo}/pay`，不会真实扣款

## 一键联调脚本（可选）

用于快速验证“前端 -> 后端 -> 数据库”主链路写入：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/db-smoke-check.ps1
```

脚本会自动验证并写入：`user_customer`、`cart_item`、`customer_order`、`order_item`、`stock_lock` 等表。
