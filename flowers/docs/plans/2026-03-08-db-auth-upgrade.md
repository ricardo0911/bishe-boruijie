# Database-backed Auth Upgrade Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace config-only demo accounts with a database-backed auth account table, support password changes, and add remember-me behavior to both login pages.

**Architecture:** Keep the current lightweight Spring Boot + JdbcTemplate auth flow, but bootstrap a dedicated auth account table on startup so existing databases upgrade in place. Persist admin/merchant accounts in MySQL, authenticate against the table, add a change-password endpoint, and let the two Vue apps choose between `sessionStorage` and `localStorage` based on a remember-me checkbox.

**Tech Stack:** Spring Boot 3, JdbcTemplate, Vue 3, Vue Router 4, Maven, JUnit 5.

### Task 1: Backend database auth foundation

**Files:**
- Modify: `backend/src/main/java/com/flowershop/service/AuthService.java`
- Modify: `backend/src/main/java/com/flowershop/controller/AuthController.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/flowershop/config/WebMvcConfig.java`
- Update: `sql/init.sql`
- Test: `backend/src/test/java/com/flowershop/service/AuthServiceTest.java`

**Step 1: Write the failing test**
- Add tests for login reading from the database-backed account source, remember-me metadata passthrough, and password changes.

**Step 2: Run test to verify it fails**
- Run: `mvn -q -Dtest=AuthServiceTest test`
- Expected: missing DB-account behavior and change-password support.

**Step 3: Write minimal implementation**
- Bootstrap `auth_account` table if missing.
- Seed admin and merchant accounts into that table if absent.
- Authenticate against the table.
- Add password change method.
- Keep merchant static root fixed.

**Step 4: Run test to verify it passes**
- Run the same targeted test command.

### Task 2: Admin console remember-me and change password

**Files:**
- Modify: `flower-admin-web/src/api/index.js`
- Modify: `flower-admin-web/src/router/index.js`
- Modify: `flower-admin-web/src/views/console/Login.vue`
- Create: `flower-admin-web/src/views/console/ChangePassword.vue`
- Modify: `flower-admin-web/src/layouts/ConsoleLayout.vue`

**Step 1: Verify current behavior fails requirement**
- Confirm admin login always persists to localStorage and has no password-change entry.

**Step 2: Implement minimal UI changes**
- Add remember-me checkbox.
- Store session in `sessionStorage` when unchecked and `localStorage` when checked.
- Add change-password route/page.
- Add a layout entry linking to the page.

**Step 3: Run build verification**
- Run: `npm run build` in `flower-admin-web`.

### Task 3: Merchant console remember-me and change password

**Files:**
- Modify: `flower-merchant-web/src/api/index.js`
- Modify: `flower-merchant-web/src/router/index.js`
- Modify: `flower-merchant-web/src/views/merchant/Login.vue`
- Create: `flower-merchant-web/src/views/merchant/ChangePassword.vue`
- Modify: `flower-merchant-web/src/layouts/MerchantLayout.vue`

**Step 1: Verify current behavior fails requirement**
- Confirm merchant login always persists to localStorage and has no password-change entry.

**Step 2: Implement minimal UI changes**
- Add remember-me checkbox.
- Store session in `sessionStorage` when unchecked and `localStorage` when checked.
- Add change-password route/page.
- Add a layout entry linking to the page.

**Step 3: Run build verification**
- Run: `npm run build` in `flower-merchant-web`.

### Task 4: End-to-end verification

**Files:**
- Verify only

**Step 1: Run backend tests**
- Run: `mvn -q -Dtest=AuthServiceTest,WebMvcConfigTest test`

**Step 2: Build both frontends**
- Run: `npm run build` in `flower-admin-web`
- Run: `npm run build` in `flower-merchant-web`

**Step 3: Smoke-check login and change password**
- Verify admin login page shows remember-me and change-password path.
- Verify merchant login page shows remember-me and change-password path.
- Verify password change succeeds and old password no longer works.
