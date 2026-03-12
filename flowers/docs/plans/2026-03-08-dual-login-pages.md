# Dual Login Pages Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add real login pages for the admin console and merchant console, and wire them to a backend login API instead of implicit localStorage defaults.

**Architecture:** Keep the existing single-token backend model, but add a dedicated auth endpoint that validates configured admin and merchant credentials and returns the existing token plus role metadata. Add route guards plus login/logout flows in both Vue apps. Also fix the merchant SPA static root so `/merchant` and `/merchant/template` stop returning 500.

**Tech Stack:** Spring Boot 3, Vue 3, Vue Router 4, Vite, JUnit 5, MockMvc.

### Task 1: Backend auth contract

**Files:**
- Create: `backend/src/main/java/com/flowershop/controller/AuthController.java`
- Create: `backend/src/main/java/com/flowershop/service/AuthService.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/flowershop/config/WebMvcConfig.java`
- Test: `backend/src/test/java/com/flowershop/controller/AuthControllerTest.java`

**Step 1: Write the failing test**
- Add MockMvc tests for successful admin login, successful merchant login, and rejected bad credentials.
- Add a test proving `/merchant` static root resolves without 500 once merchant root points at the real folder.

**Step 2: Run test to verify it fails**
- Run: `mvn -q -Dtest=AuthControllerTest test`
- Expected: missing endpoint / failing merchant root behavior.

**Step 3: Write minimal implementation**
- Add `/api/v1/auth/login` endpoint.
- Validate `account` + `password` + `loginType` against config.
- Return `{ token, role, displayName, landingPath }`.
- Exclude auth login from interceptor.
- Fix merchant web root config from `../flower-web/` to `../flower-merchant-web/`.

**Step 4: Run test to verify it passes**
- Run the same targeted backend test command.

### Task 2: Admin console login flow

**Files:**
- Modify: `flower-admin-web/src/router/index.js`
- Modify: `flower-admin-web/src/api/index.js`
- Create: `flower-admin-web/src/views/console/Login.vue`
- Modify: `flower-admin-web/src/layouts/ConsoleLayout.vue`
- Modify: `flower-admin-web/index.html`

**Step 1: Write the failing test**
- If no frontend test harness exists, use backend/API verification plus production build as regression proof.
- Define expected behavior: unauthenticated visits redirect to `/login`; successful login lands on `/`; logout clears auth and returns to `/login`.

**Step 2: Verify current behavior fails requirement**
- Confirm current app opens without login and relies on localStorage defaults.

**Step 3: Write minimal implementation**
- Add login route/page.
- Add auth storage helpers and route guards.
- Replace implicit fallback role/token with stored session only.
- Add logout action in console layout.

**Step 4: Run build verification**
- Run: `npm run build` in `flower-admin-web`.

### Task 3: Merchant console login flow

**Files:**
- Modify: `flower-merchant-web/src/router/index.js`
- Modify: `flower-merchant-web/src/api/index.js`
- Create: `flower-merchant-web/src/views/merchant/Login.vue`
- Modify: `flower-merchant-web/src/layouts/MerchantLayout.vue`
- Modify: `flower-merchant-web/index.html`

**Step 1: Write the failing test**
- Use API + build verification as above.
- Define expected behavior: unauthenticated visits redirect to `/merchant/login`; successful login lands on `/merchant`; logout clears auth and returns to `/merchant/login`.

**Step 2: Verify current behavior fails requirement**
- Confirm current merchant app hardcodes `MERCHANT` role and token.

**Step 3: Write minimal implementation**
- Add merchant login route/page.
- Add auth storage helpers and route guards.
- Use backend login endpoint with `loginType: MERCHANT`.
- Add logout action in merchant layout.

**Step 4: Run build verification**
- Run: `npm run build` in `flower-merchant-web`.

### Task 4: End-to-end verification

**Files:**
- Verify only

**Step 1: Run targeted backend tests**
- Run: `mvn -q -Dtest=AuthControllerTest test`

**Step 2: Build both frontends**
- Run: `npm run build` in `flower-admin-web`
- Run: `npm run build` in `flower-merchant-web`

**Step 3: Smoke-check running app**
- Verify `http://127.0.0.1:18080/admin` redirects to login page when logged out.
- Verify `http://127.0.0.1:18080/merchant` redirects to merchant login page when logged out.
- Verify successful admin login and merchant login each reach the correct landing page.
