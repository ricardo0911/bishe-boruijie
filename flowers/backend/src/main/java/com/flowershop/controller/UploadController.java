package com.flowershop.controller;

import com.flowershop.common.ApiResponse;
import com.flowershop.service.ImageStorageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final ImageStorageService imageStorageService;

    public UploadController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/image")
    public ApiResponse<String> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ApiResponse.fail("UPLOAD_EMPTY", "Please choose an image to upload");
        }

        if (file.getSize() > MAX_SIZE) {
            return ApiResponse.fail("FILE_TOO_LARGE", "Image size must not exceed 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ApiResponse.fail("INVALID_TYPE", "Only JPG/PNG/GIF/WEBP images are supported");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }

        String newFileName = UUID.randomUUID() + ext;
        return ApiResponse.success(imageStorageService.storeUploadedImage(file, newFileName));
    }
}