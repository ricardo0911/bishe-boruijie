# Feedback Management Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add real feedback management so users can view their submitted support tickets and admins can clearly manage them as feedback work items.

**Architecture:** Reuse the existing support-ticket domain as the feedback model. Expose a user-facing ticket list endpoint in the backend, surface a miniapp “我的反馈” list page plus entry points, and polish the admin console labels/API helpers so the management area is clearly discoverable.

**Tech Stack:** Spring Boot + JdbcTemplate, Vue 3 + Vite admin console, WeChat miniapp pages, JUnit 5 + Mockito.

### Task 1: Backend user feedback list API

**Files:**
- Modify: `backend/src/main/java/com/flowershop/service/SupportTicketService.java`
- Modify: `backend/src/main/java/com/flowershop/controller/SupportTicketController.java`
- Modify: `backend/src/main/java/com/flowershop/config/AdminTokenInterceptor.java`
- Test: `backend/src/test/java/com/flowershop/service/SupportTicketServiceTest.java`
- Test: `backend/src/test/java/com/flowershop/config/AdminTokenInterceptorTest.java`

**Steps:**
1. Write a failing service test for listing a user’s own support tickets.
2. Write a failing interceptor test for public access to `GET /api/v1/support-tickets/user/{userId}`.
3. Implement minimal backend query + endpoint + public path rule.
4. Run focused backend tests.

### Task 2: Miniapp feedback history page

**Files:**
- Create: `miniapp-user/pages/support-ticket-list/support-ticket-list.js`
- Create: `miniapp-user/pages/support-ticket-list/support-ticket-list.wxml`
- Create: `miniapp-user/pages/support-ticket-list/support-ticket-list.wxss`
- Create: `miniapp-user/pages/support-ticket-list/support-ticket-list.json`
- Modify: `miniapp-user/pages/profile/profile.js`
- Modify: `miniapp-user/pages/profile/profile.wxml`
- Modify: `miniapp-user/pages/service/service.js`
- Modify: `miniapp-user/pages/service/service.wxml`
- Modify: `miniapp-user/app.json`

**Steps:**
1. Add a user ticket history page that lists status, content, processing notes, and timestamps.
2. Add entry points from profile and service center.
3. Keep UI minimal and consistent with existing pages.
4. Run miniapp syntax/JSON checks.

### Task 3: Admin discoverability polish

**Files:**
- Modify: `flower-admin-web/src/layouts/ConsoleLayout.vue`
- Modify: `flower-admin-web/src/views/console/SupportTickets.vue`
- Modify: `flower-admin-web/src/api/index.js`

**Steps:**
1. Rename visible labels from “客服工单” to “反馈管理” where it improves discoverability.
2. Add API helpers for support-ticket list/detail/process calls.
3. Switch the page to those helpers and run admin build.
