package com.flowershop.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ImageStorageServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void storeUploadedImagePersistsBytesAndReturnsLegacyUrl() throws Exception {
        ImageStorageService imageStorageService = new ImageStorageService(jdbcTemplate, tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "rose.png",
            "image/png",
            new byte[]{1, 2, 3, 4}
        );

        String url = imageStorageService.storeUploadedImage(file, "abc.png");

        assertEquals("/uploads/abc.png", url);
        verify(jdbcTemplate).update(
            contains("INSERT INTO stored_image"),
            eq("abc.png"),
            eq("rose.png"),
            eq("image/png"),
            eq(4L),
            any(byte[].class)
        );
    }

    @Test
    void loadImageReturnsStoredDatabaseImage() throws Exception {
        ImageStorageService imageStorageService = new ImageStorageService(jdbcTemplate, tempDir.toString());
        byte[] content = new byte[]{9, 8, 7};
        doReturn(List.of(new ImageStorageService.StoredImageContent("db.png", "image/png", content, content.length)))
            .when(jdbcTemplate)
            .query(contains("FROM stored_image"), any(RowMapper.class), eq("db.png"));

        ImageStorageService.StoredImageContent image = imageStorageService.loadImage("db.png");

        assertEquals("db.png", image.fileName());
        assertEquals("image/png", image.contentType());
        assertArrayEquals(content, image.content());
    }

    @Test
    void loadImageImportsExistingDiskFileWhenDatabaseMisses() throws Exception {
        Path diskFile = tempDir.resolve("legacy.png");
        byte[] content = new byte[]{5, 4, 3, 2};
        Files.write(diskFile, content);

        ImageStorageService imageStorageService = new ImageStorageService(jdbcTemplate, tempDir.toString());
        doReturn(List.of())
            .when(jdbcTemplate)
            .query(contains("FROM stored_image"), any(RowMapper.class), eq("legacy.png"));

        ImageStorageService.StoredImageContent image = imageStorageService.loadImage("legacy.png");

        assertEquals("legacy.png", image.fileName());
        assertArrayEquals(content, image.content());
        verify(jdbcTemplate).update(
            contains("INSERT INTO stored_image"),
            eq("legacy.png"),
            eq("legacy.png"),
            eq("image/png"),
            eq(4L),
            any(byte[].class)
        );
    }
}