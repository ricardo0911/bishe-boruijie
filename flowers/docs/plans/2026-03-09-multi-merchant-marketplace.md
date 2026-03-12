# Multi-Merchant Marketplace Visibility Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reflect multi-merchant flower selection in the user miniapp by exposing merchant info from backend product APIs and displaying it across key shopping flows.

**Architecture:** Keep the existing order model unchanged, but expose `merchantAccount` and `merchantName` on product-facing APIs and cart queries. Update the miniapp to normalize merchant info once and render it on list, detail, cart, and checkout surfaces so users can tell which flower belongs to which merchant.

**Tech Stack:** Spring Boot, JdbcTemplate, Java records, WeChat Mini Program JS/WXML/WXSS.

### Task 1: Lock behavior with backend tests

**Files:**
- Modify: `backend/src/test/java/com/flowershop/service/ProductServiceTest.java`

**Step 1: Write the failing test**
- Assert `ProductView` and `ProductDetailView` expose merchant accessors.
- Assert `ProductService` product queries join merchant account display data.

**Step 2: Run test to verify it fails**
- Run: `mvn -q "-Dtest=ProductServiceTest" test`
- Expected: compile/test failure before merchant fields are added.

**Step 3: Write minimal implementation**
- Add merchant fields to DTO records and product snapshot mapping.

**Step 4: Run targeted verification**
- Compile changed backend classes and the updated test class.

### Task 2: Expose merchant info in backend product/cart queries

**Files:**
- Modify: `backend/src/main/java/com/flowershop/dto/ProductView.java`
- Modify: `backend/src/main/java/com/flowershop/dto/ProductRecommendView.java`
- Modify: `backend/src/main/java/com/flowershop/dto/ProductDetailView.java`
- Modify: `backend/src/main/java/com/flowershop/service/ProductService.java`
- Modify: `backend/src/main/java/com/flowershop/service/CartService.java`

**Step 1: Join merchant display info**
- Join `auth_account aa` on `p.merchant_account = aa.account` and `aa.role_code = 'MERCHANT'`.
- Fallback merchant name to account, then to `????`.

**Step 2: Keep scope minimal**
- Do not redesign order splitting or merchant-level checkout.
- Only expose merchant identity for display.

**Step 3: Verify compilation**
- Compile the touched backend classes with `javac` against the existing classpath.

### Task 3: Show merchant info on the miniapp

**Files:**
- Modify: `miniapp-user/utils/format.js`
- Modify: `miniapp-user/pages/home/home.js`
- Modify: `miniapp-user/pages/home/home.wxml`
- Modify: `miniapp-user/pages/home/home.wxss`
- Modify: `miniapp-user/pages/category/category.js`
- Modify: `miniapp-user/pages/category/category.wxml`
- Modify: `miniapp-user/pages/category/category.wxss`
- Modify: `miniapp-user/pages/detail/detail.js`
- Modify: `miniapp-user/pages/detail/detail.wxml`
- Modify: `miniapp-user/pages/detail/detail.wxss`
- Modify: `miniapp-user/pages/cart/cart.js`
- Modify: `miniapp-user/pages/cart/cart.wxml`
- Modify: `miniapp-user/pages/cart/cart.wxss`
- Modify: `miniapp-user/pages/checkout/checkout.js`
- Modify: `miniapp-user/pages/checkout/checkout.wxml`
- Modify: `miniapp-user/pages/checkout/checkout.wxss`

**Step 1: Normalize merchant name once**
- Add a shared helper in `utils/format.js`.

**Step 2: Render merchant name in key surfaces**
- Home recommendation/hot/inspiration cards.
- Category product cards.
- Product detail header.
- Cart items and cart recommendations.
- Checkout item list.

**Step 3: Sanity-check syntax**
- Run `node` syntax checks on changed JS files.

### Task 4: Final verification and handoff

**Step 1: Record blockers honestly**
- If full Maven test execution is blocked by unrelated repository issues, document the blocker and keep targeted verification evidence.

**Step 2: Provide clickable file references**
- Summarize the backend API change points and the miniapp display change points.
