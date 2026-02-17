# Analysis Service

Python 分析服务负责两件事：

1. 销量预测并生成补货建议
2. 生成用户推荐结果

## 启动

```bash
pip install -r requirements.txt
uvicorn service:app --reload --port 8001
```

默认数据库配置与后端一致：`root/root@localhost:3306/flower_shop`。
可通过环境变量覆盖：`DB_URL`（优先）或 `DB_USERNAME/DB_PASSWORD/DB_HOST/DB_PORT/DB_NAME`。

## 接口

- `GET /health`
- `POST /forecast/run?days=14&lead_time_days=2&z_value=1.28`
- `POST /recommendation/run?top_n=5`

## 说明

- 优先使用 Prophet 建模。
- 当 Prophet 不可用或样本不足时自动使用移动平均。
- 结果写入：`forecast_result`、`replenishment_suggestion`、`recommendation_result`。
