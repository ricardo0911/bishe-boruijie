# Product Delete Action Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a usable delete action to merchant product management so a merchant can remove a product from the list.

**Architecture:** Keep the existing merchant products page and `/api/v1/products` resource. Add a backend delete endpoint plus service-layer cleanup for dependent records, then wire a confirmed delete button in the Vue list and refresh the table after deletion.

**Tech Stack:** Vue 3, Vite, Spring Boot, JdbcTemplate, JUnit 5, Mockito.

### Task 1: Lock in backend deletion behavior

**Files:**
- Create: `backend/src/test/java/com/flowershop/service/ProductServiceTest.java`
- Modify: `backend/src/main/java/com/flowershop/service/ProductService.java`

**Step 1: Write the failing test**

Add tests that verify `deleteProduct(productId)`:
- deletes dependent rows from `user_favorite`, `cart_item`, `product_bom`, `recommendation_result`
- deletes the target row from `product`
- throws `BusinessException` when the product does not exist

**Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ProductServiceTest test`
Expected: FAIL because `deleteProduct` does not exist yet.

**Step 3: Write minimal implementation**

Implement `deleteProduct(Long productId)` in `ProductService` with transactional cleanup and not-found protection.

**Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ProductServiceTest test`
Expected: PASS.

### Task 2: Expose backend delete API

**Files:**
- Modify: `backend/src/main/java/com/flowershop/controller/ProductController.java`

**Step 1: Add endpoint**

Add `DELETE /api/v1/products/{productId}` delegating to `productService.deleteProduct(productId)`.

**Step 2: Run focused backend test/build**

Run: `mvn -q -Dtest=ProductServiceTest test`
Expected: PASS.

### Task 3: Add merchant delete button

**Files:**
- Modify: `flower-merchant-web/src/views/merchant/Products.vue`

**Step 1: Update UI**

Render `编辑` + `删除` actions in the operation column using existing button styles.

**Step 2: Add delete handler**

Use `confirm(...)`, call `api.del('/products/' + id)`, show success/failure toast, and reload the list after success.

**Step 3: Verify with build**

Run: `cmd /c npm run build`
Expected: PASS.
