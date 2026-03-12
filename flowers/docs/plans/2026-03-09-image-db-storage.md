# Image DB Storage Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Store uploaded product images in the database and serve `/uploads/...` requests from the database so image links do not break when local files disappear.

**Architecture:** Keep the existing upload API shape, but replace filesystem persistence with DB persistence through a dedicated image storage service. Add a controller that serves legacy `/uploads/{file}` URLs from the database and lazily imports any surviving disk files into the database for backward compatibility.

**Tech Stack:** Spring Boot, JdbcTemplate, MultipartFile, MySQL LONGBLOB, WeChat Mini Program/Admin existing image URLs.

### Task 1: Lock behavior with tests
- Add `backend/src/test/java/com/flowershop/service/ImageStorageServiceTest.java`
- Cover upload persistence, DB load, and disk-to-DB lazy import.

### Task 2: Implement DB-backed image storage
- Add `backend/src/main/java/com/flowershop/service/ImageStorageService.java`
- Auto-create `stored_image` table on startup.
- Store image bytes and metadata with stable legacy file names.

### Task 3: Serve legacy upload URLs from DB
- Add `backend/src/main/java/com/flowershop/controller/StoredImageController.java`
- Keep `/uploads/{file}` working.
- Add `/api/v1/upload/image/{file}` as an extra debug/read route.

### Task 4: Switch upload writes to DB
- Modify `backend/src/main/java/com/flowershop/controller/UploadController.java`
- Preserve returned URL format as `/uploads/{file}` so frontend code does not need to change.

### Task 5: Verify targeted flows
- Compile changed backend classes.
- Compile new tests.
- Re-run miniapp/admin uploads after backend restart.