<template>
  <div class="merchant-template">
    <div class="top-bar">
      <h1 class="page-title">卖花商家模板</h1>
      <button class="btn btn-primary">复制模板开始写</button>
    </div>

    <div class="card template-hero">
      <div class="template-hero__head">
        <h2>鲜花商家启动模板</h2>
        <span class="badge badge-success">可复用</span>
      </div>
      <p>
        这个页面可作为商家端启动模板：补齐数据源、替换示例文案，并保留模块结构以便快速交付。
      </p>
      <div class="template-hero__tags">
        <span class="tag">店铺档案</span>
        <span class="tag">销售看板</span>
        <span class="tag">活动计划</span>
        <span class="tag">履约SOP</span>
      </div>
    </div>

    <div class="template-grid">
      <section class="card module-card">
        <div class="card-header">
          <span class="card-title">店铺信息模板</span>
        </div>
        <div class="card-body">
          <div class="kv-grid">
            <div class="kv-item"><span class="kv-label">店铺名</span><span class="kv-value">花之都·城市店</span></div>
            <div class="kv-item"><span class="kv-label">主营品类</span><span class="kv-value">生日花束 / 节日礼盒 / 企业用花</span></div>
            <div class="kv-item"><span class="kv-label">配送范围</span><span class="kv-value">主城 8km，2 小时达</span></div>
            <div class="kv-item"><span class="kv-label">营业时间</span><span class="kv-value">08:00 - 22:00</span></div>
          </div>
        </div>
      </section>

      <section class="card module-card">
        <div class="card-header">
          <span class="card-title">运营指标模板</span>
        </div>
        <div class="card-body">
          <div class="kpi-grid">
            <div v-for="item in kpiItems" :key="item.label" class="kpi-item">
              <div class="kpi-label">{{ item.label }}</div>
              <div class="kpi-value">{{ item.value }}</div>
              <div class="kpi-hint">{{ item.hint }}</div>
            </div>
          </div>
        </div>
      </section>

      <section class="card module-card">
        <div class="card-header">
          <span class="card-title">热销花束模板</span>
        </div>
        <div class="card-body">
          <div class="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>商品</th>
                  <th>场景</th>
                  <th>价格带</th>
                  <th>建议卖点</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in bouquetItems" :key="item.name">
                  <td>{{ item.name }}</td>
                  <td>{{ item.scene }}</td>
                  <td>{{ item.price }}</td>
                  <td>{{ item.pitch }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="card module-card">
        <div class="card-header">
          <span class="card-title">活动日历模板</span>
        </div>
        <div class="card-body">
          <ul class="campaign-list">
            <li v-for="item in campaignItems" :key="item.name">
              <div class="campaign-title">{{ item.name }}</div>
              <div class="campaign-desc">{{ item.desc }}</div>
              <div class="campaign-meta">{{ item.when }}</div>
            </li>
          </ul>
        </div>
      </section>

      <section class="card module-card module-card--full">
        <div class="card-header">
          <span class="card-title">履约与客服 SOP 模板</span>
        </div>
        <div class="card-body">
          <div class="sop-grid">
            <div>
              <h3 class="sop-title">订单履约</h3>
              <ol class="sop-list">
                <li v-for="item in fulfillmentSteps" :key="item">{{ item }}</li>
              </ol>
            </div>
            <div>
              <h3 class="sop-title">客诉处理</h3>
              <ol class="sop-list">
                <li v-for="item in supportSteps" :key="item">{{ item }}</li>
              </ol>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
const kpiItems = [
  { label: "今日订单", value: "68", hint: "较昨日 +12%" },
  { label: "销售额", value: "¥9,820", hint: "客单价 ¥144.4" },
  { label: "待配送", value: "11", hint: "30 分钟内需发出" },
  { label: "退款率", value: "1.8%", hint: "目标 < 2.0%" },
];

const bouquetItems = [
  { name: "红玫瑰经典款", scene: "纪念日 / 告白", price: "¥199 - ¥299", pitch: "同城急送，含手写卡片" },
  { name: "向日葵治愈款", scene: "探望 / 鼓励", price: "¥129 - ¥189", pitch: "明亮配色，低预算高满意" },
  { name: "混搭轻奢礼盒", scene: "商务 / 节庆", price: "¥299 - ¥499", pitch: "礼盒包装，支持企业发票" },
];

const campaignItems = [
  { name: "周末买赠活动", desc: "满 199 赠迷你花束，拉升客单价", when: "每周五至周日" },
  { name: "会员复购券", desc: "下单 7 天后自动发 15 元券", when: "长期进行" },
  { name: "节日预售专场", desc: "预售档位+配送排期，降低履约压力", when: "节日前 14 天" },
];

const fulfillmentSteps = [
  "接单后 3 分钟内确认库存和配送时段",
  "花材分拣完成后上传打包照片",
  "骑手出发时通知用户并同步预计送达时间",
  "签收后回收评价，异常订单 10 分钟内回访",
];

const supportSteps = [
  "先确认问题类型：时效/花材/破损/服务",
  "收集证据：订单号+照片+沟通记录",
  "按标准给出补偿：重送/退款/优惠券",
  "24 小时内复盘并更新黑名单与质检规则",
];
</script>

<style scoped>
.merchant-template {
  display: grid;
  gap: 16px;
}

.template-hero {
  padding: 18px;
}

.template-hero__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.template-hero__head h2 {
  margin: 0;
  font-size: 20px;
  color: var(--text);
}

.template-hero p {
  margin: 0;
  color: var(--text-secondary);
}

.template-hero__tags {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--rose-700);
  background: rgba(248, 223, 232, 0.8);
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.module-card {
  min-height: 250px;
}

.module-card--full {
  grid-column: 1 / -1;
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.kv-item {
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.7);
}

.kv-label {
  display: block;
  font-size: 12px;
  color: var(--text-muted);
}

.kv-value {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-weight: 600;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.kpi-item {
  border-radius: 12px;
  border: 1px solid var(--line);
  padding: 12px;
  background: rgba(255, 255, 255, 0.7);
}

.kpi-label {
  font-size: 12px;
  color: var(--text-muted);
}

.kpi-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: var(--rose-700);
}

.kpi-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.campaign-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 12px;
}

.campaign-list li {
  padding: 12px;
  border-radius: 10px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.72);
}

.campaign-title {
  font-weight: 700;
  color: var(--text);
}

.campaign-desc {
  margin-top: 4px;
  color: var(--text-secondary);
}

.campaign-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.sop-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.sop-title {
  margin: 0 0 10px;
  font-size: 16px;
  color: var(--text);
}

.sop-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  display: grid;
  gap: 8px;
}

@media (max-width: 1200px) {
  .template-grid {
    grid-template-columns: 1fr;
  }

  .module-card--full {
    grid-column: auto;
  }
}

@media (max-width: 760px) {
  .kv-grid,
  .kpi-grid,
  .sop-grid {
    grid-template-columns: 1fr;
  }
}
</style>
