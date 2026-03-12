package com.flowershop.controller;

import com.flowershop.exception.BusinessException;
import com.flowershop.service.ImageStorageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

@RestController
public class StoredImageController {

    private final ImageStorageService imageStorageService;

    public StoredImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @GetMapping({"/uploads/{fileName:.+}", "/api/v1/upload/image/{fileName:.+}"})
    public ResponseEntity<byte[]> getImage(@PathVariable String fileName) throws IOException {
        try {
            ImageStorageService.StoredImageContent image = imageStorageService.loadImage(fileName);
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(image.contentType());
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
            return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(image.contentLength())
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(image.content());
        } catch (BusinessException ex) {
            if ("IMAGE_NOT_FOUND".equals(ex.getCode())) {
                return ResponseEntity.notFound().build();
            }
            throw ex;
        }
    }
}