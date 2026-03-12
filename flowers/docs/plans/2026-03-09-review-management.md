# 评论功能补齐 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为花店项目补齐独立的评论功能，让用户可以在订单完成后提交并查看自己的评价，让管理端可以查看并管理评论内容。

**Architecture:** 保留现有 `review` 表和商品详情页评论展示逻辑，在后端补充“评论列表/删除/可评价订单”接口；在小程序补充“我的评价”和“发表评价”入口；在管理端补充评论管理页。尽量复用现有订单与用户查询能力，避免改动既有详情页评论逻辑。

**Tech Stack:** Spring Boot 3 + JdbcTemplate、Vue 3、微信小程序原生框架。

### Task 1: 补后端评论管理接口

**Files:**
- Modify: `backend/src/main/java/com/flowershop/service/ReviewService.java`
- Modify: `backend/src/main/java/com/flowershop/controller/ReviewController.java`
- Test: `backend/src/test/java/com/flowershop/service/ReviewServiceTest.java`

**Steps:**
1. 先写失败测试，覆盖“查询用户可评价订单 / 查询全部评论 / 删除评论”。
2. 最小实现后端查询与删除接口。
3. 跑测试确认通过。

### Task 2: 补小程序评论入口与我的评价页

**Files:**
- Modify: `miniapp-user/app.json`
- Modify: `miniapp-user/pages/orders/orders.js`
- Modify: `miniapp-user/pages/orders/orders.wxml`
- Modify: `miniapp-user/pages/profile/profile.js`
- Modify: `miniapp-user/pages/profile/profile.wxml`
- Create: `miniapp-user/pages/review-create/*`
- Create: `miniapp-user/pages/review-list/*`

**Steps:**
1. 在已完成订单增加“去评价/已评价”动作。
2. 在个人中心增加“我的评价”入口。
3. 新建评价提交页与评价列表页。

### Task 3: 补管理端评论管理页

**Files:**
- Modify: `flower-admin-web/src/api/index.js`
- Modify: `flower-admin-web/src/router/index.js`
- Modify: `flower-admin-web/src/layouts/ConsoleLayout.vue`
- Create: `flower-admin-web/src/views/console/Reviews.vue`

**Steps:**
1. 增加评论查询/删除 API 封装。
2. 增加评论管理路由与菜单入口。
3. 实现管理端评论列表、筛选和删除操作。

### Task 4: 验证

**Files:**
- Verify: `backend/pom.xml`
- Verify: `flower-admin-web/package.json`

**Steps:**
1. 跑后端测试。
2. 跑管理端构建。
3. 若小程序无自动测试基建，至少做页面注册和静态代码校验。
