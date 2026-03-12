# Miniapp Logistics Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Let merchant shipping data flow through the backend and show a real logistics details page in the user miniapp.

**Architecture:** Extend the existing order confirm flow to accept logistics company, tracking number, and shipping time, persist them on `customer_order`, and return them in order detail/list APIs. Reuse order detail data in a new miniapp logistics page so the feature ships without adding a separate logistics service dependency.

**Tech Stack:** Spring Boot + JdbcTemplate, WeChat Mini Program.

### Task 1: Define logistics contract

**Files:**
- Create: `backend/src/main/java/com/flowershop/dto/ConfirmOrderRequest.java`
- Modify: `backend/src/main/java/com/flowershop/controller/OrderController.java`

**Step 1:** Write a failing backend test for confirming an order with logistics payload.

**Step 2:** Update the confirm endpoint to accept an optional request body.

**Step 3:** Run the focused backend test and confirm it fails for the missing contract.

### Task 2: Persist logistics fields in orders

**Files:**
- Modify: `backend/src/main/java/com/flowershop/service/OrderService.java`
- Modify: `backend/src/main/java/com/flowershop/dto/OrderDetailResponse.java`
- Modify: `backend/src/main/java/com/flowershop/dto/OrderSummaryResponse.java`

**Step 1:** Write failing tests for returning logistics fields in order detail/list responses.

**Step 2:** Add schema guards for `tracking_company`, `tracking_no`, and `shipped_at`.

**Step 3:** Persist logistics info when confirming shipment and include it in order queries.

**Step 4:** Re-run focused backend tests until they pass.

### Task 3: Add miniapp logistics page

**Files:**
- Modify: `miniapp-user/app.json`
- Create: `miniapp-user/pages/logistics/logistics.js`
- Create: `miniapp-user/pages/logistics/logistics.wxml`
- Create: `miniapp-user/pages/logistics/logistics.wxss`
- Create: `miniapp-user/pages/logistics/logistics.json`
- Modify: `miniapp-user/pages/order-detail/order-detail.js`
- Modify: `miniapp-user/pages/orders/orders.js`

**Step 1:** Navigate `查看物流` to the new page with `orderNo`.

**Step 2:** Fetch order detail on the logistics page and render company, tracking number, shipping time, receiver info, and a simple timeline.

**Step 3:** Add graceful empty states when no tracking number exists.

### Task 4: Verify the end-to-end flow

**Files:**
- Test: `backend/src/test/java/com/flowershop/service/OrderServiceTest.java`

**Step 1:** Run focused backend tests.

**Step 2:** Build the miniapp bundle if available, or verify page wiring by source inspection.

**Step 3:** Summarize the manual verification path for merchant ship -> user view logistics.
