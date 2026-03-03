# Flower Shop Smart Management System

基于你提供的开题方向，我先落地了一个可直接二开的毕业设计项目骨架，覆盖：

- 微信小程序/后台系统需要的核心 REST API（Spring Boot）
- 花店业务关键链路：下单锁库存、支付扣减、取消/退款回滚
- FEFO 批次库存管理与低库存预警
- Python 分析服务（销量预测 + 补货建议 + 简单推荐）
- 可执行的 MySQL 建表脚本与初始化数据

## 1. 项目结构

```text
.
├─ backend/                     # Spring Boot 后端
├─ miniapp-user/                # 微信小程序（用户端）
├─ flower-web/                  # 前端页面（用户端/商家端/管理员端）
├─ analysis/                    # Python 分析服务（FastAPI）
├─ db/                          # MySQL 建表与种子数据
├─ docs/                        # 接口清单、系统设计模板、架构说明
└─ README.md
```

## 2. 技术栈

- 后端：Spring Boot 3 + Spring MVC + JDBC + MySQL 8
- 分析层：Python + Pandas + NumPy + Prophet(可选) + SQLAlchemy + FastAPI
- 文档：OpenAPI（通过 springdoc 自动生成）

说明：
- 你开题里写 MyBatis/MyBatis-Plus，这个版本为了可快速跑通先采用 JDBC 事务实现核心库存一致性。
- 后续如需答辩“对齐开题”，可无缝替换持久层实现，不影响业务分层与接口定义。

## 3. 快速启动

### 3.1 初始化数据库

1) 新建 MySQL 数据库并执行脚本：

- `db/schema.sql`
- `db/seed.sql`

2) 默认数据库名：`flower_shop`

### 3.2 启动后端

```bash
cd backend
mvn spring-boot:run
```

如需使用 jar 启动，请先执行 `mvn clean package`，再运行 `java -jar target/flower-shop-backend-1.0.0.jar`，避免误用旧构建产物。

默认端口 `18080`，Swagger 地址：

- `http://localhost:18080/swagger-ui/index.html`

### 3.2.1 可选：订单链路 API 冒烟测试

在后端启动后，可执行以下脚本快速验证 `创建订单 -> 支付 -> 退款回滚` 主链路：

```bash
python backend/smoke_api.py
```

可选参数示例：

```bash
python backend/smoke_api.py --user-id 1 --product-id 2 --quantity 1
```

### 3.3 启动分析服务

```bash
cd analysis
pip install -r requirements.txt
uvicorn service:app --reload --port 8001
```

默认端口 `8001`。

### 3.4 前端访问

先构建前端：

```bash
cd flower-web
npm install
npm run build
```

启动后端后，直接访问：

- `http://localhost:18080/merchant`
- `http://localhost:18080/admin`

说明：
- 后端对管理类接口启用了 `X-Admin-Token` 校验，默认值为 `please-change-admin-token`。
- 生产环境请通过 `ADMIN_TOKEN`（后端）和 `VITE_ADMIN_TOKEN`（前端构建时）替换默认值。

### 3.5 微信小程序用户端

1) 使用微信开发者工具导入 `miniapp-user`。

2) 本地开发请关闭域名校验（开发工具设置）。

3) 默认后端地址：`http://127.0.0.1:18080/api/v1`（可在 `miniapp-user/app.js` 修改）。

4) 小程序为调试友好模式：

- 假登录（mock openid）
- 假支付（模拟支付按钮，不产生真实扣款）

5) 一键数据库联调脚本（可选）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/db-smoke-check.ps1
```

如果后端不在 `18080`，可指定：`-BaseUrl http://localhost:19080/api/v1 -HealthUrl http://localhost:19080/actuator/health`。

6) 批量造数脚本（用于调试）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/api-seed-debug-data.ps1 -UserCount 8 -OrdersPerUser 4
```

该脚本通过后端 API 写入数据（不需要直接连 MySQL），会生成用户、购物车、订单及库存锁流水，便于联调与压测。

7) 图片 + 推荐销量造数（用于展示）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/api-seed-visual-data.ps1 -OrderCount 36
```

该脚本会补齐花材/商品图片数据，并生成近期销量订单样本，便于首页推荐与图片展示调试。

## 4. 关键业务能力

- 自动计价：`花束价格 = BOM花材总价 + 包装费 + 配送费`
- 下单锁库存：按 FEFO（先到期先出）分配批次并锁定库存
- 支付扣减：支付成功后从锁定库存转为正式扣减
- 取消/退款回滚：取消释放锁定库存；已支付订单回补库存
- 同城配送：结算页支持标准/急送/定时配送，自动计算配送费并写入订单备注
- 库存预警：可用库存低于阈值自动预警
- 智能预测：输出未来 14 天预测与补货建议
- 推荐结果：根据用户历史和近期热销给出候选商品
- 创意体验：小程序与 H5 首页支持“送花灵感实验室”（场景+预算推荐 + 祝福文案一键复制）

## 5. 对答辩可讲的创新点

- 业务闭环：交易、库存、评价、预测、补货建议打通
- 行业特性：针对鲜花易腐性引入 FEFO + 批次质量管理
- 轻量智能：可解释模型（移动平均/Prophet）优先，稳定可落地
- 低成本部署：微信生态 + Java 主服务 + Python 分析服务

## 6. 下一步建议

1. 对接微信小程序页面（首页、商品、购物车、订单）
2. 增加 Vue 3 商家后台（订单处理、库存预警、报表）
3. 补全支付回调、售后流程、权限控制（RBAC）
4. 用真实历史数据调参并补充实验对比图表
