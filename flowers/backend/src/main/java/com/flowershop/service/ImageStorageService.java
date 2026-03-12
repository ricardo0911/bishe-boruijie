package com.flowershop.service;

import com.flowershop.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class ImageStorageService {

    private final JdbcTemplate jdbcTemplate;
    private final Path uploadPath;

    public ImageStorageService(JdbcTemplate jdbcTemplate, @Value("${app.upload-dir:uploads/}") String uploadDir) {
        this.jdbcTemplate = jdbcTemplate;
        this.uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void ensureSchema() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS stored_image (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                file_name VARCHAR(160) NOT NULL,
                original_name VARCHAR(255) DEFAULT NULL,
                content_type VARCHAR(80) NOT NULL,
                content_length BIGINT NOT NULL,
                content_data LONGBLOB NOT NULL,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY uk_stored_image_file_name (file_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Uploaded images stored in DB'
            """);

        try {
            Files.createDirectories(uploadPath);
            importExistingDiskUploads();
        } catch (IOException ignored) {
        }
    }

    public String storeUploadedImage(MultipartFile file, String storedFileName) throws IOException {
        String safeFileName = normalizeFileName(storedFileName);
        byte[] content = file.getBytes();
        String contentType = normalizeContentType(file.getContentType(), safeFileName);
        String originalName = trimToNull(file.getOriginalFilename());
        upsertImage(safeFileName, originalName, contentType, content);
        return "/uploads/" + safeFileName;
    }

    public StoredImageContent loadImage(String fileName) throws IOException {
        String safeFileName = normalizeFileName(fileName);
        StoredImageContent storedImage = findImage(safeFileName);
        if (storedImage != null) {
            return storedImage;
        }

        Path diskFile = resolveDiskFile(safeFileName);
        if (Files.exists(diskFile) && Files.isRegularFile(diskFile)) {
            byte[] content = Files.readAllBytes(diskFile);
            String contentType = detectContentType(diskFile, safeFileName);
            upsertImage(safeFileName, safeFileName, contentType, content);
            return new StoredImageContent(safeFileName, contentType, content, content.length);
        }

        throw new BusinessException("IMAGE_NOT_FOUND", "Image not found: " + safeFileName);
    }

    private void importExistingDiskUploads() throws IOException {
        try (Stream<Path> files = Files.list(uploadPath)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String fileName = path.getFileName().toString();
                if (exists(fileName)) {
                    return;
                }
                try {
                    byte[] content = Files.readAllBytes(path);
                    String contentType = detectContentType(path, fileName);
                    upsertImage(fileName, fileName, contentType, content);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private boolean exists(String fileName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM stored_image WHERE file_name = ?",
            Integer.class,
            fileName
        );
        return count != null && count > 0;
    }

    private StoredImageContent findImage(String fileName) {
        List<StoredImageContent> images = jdbcTemplate.query(
            """
            SELECT file_name, content_type, content_length, content_data
            FROM stored_image
            WHERE file_name = ?
            """,
            storedImageRowMapper(),
            fileName
        );
        return images.isEmpty() ? null : images.get(0);
    }

    private RowMapper<StoredImageContent> storedImageRowMapper() {
        return (rs, rowNum) -> new StoredImageContent(
            rs.getString("file_name"),
            rs.getString("content_type"),
            rs.getBytes("content_data"),
            rs.getLong("content_length")
        );
    }

    @Transactional
    protected void upsertImage(String fileName, String originalName, String contentType, byte[] content) {
        jdbcTemplate.update(
            """
            INSERT INTO stored_image(file_name, original_name, content_type, content_length, content_data, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                original_name = VALUES(original_name),
                content_type = VALUES(content_type),
                content_length = VALUES(content_length),
                content_data = VALUES(content_data),
                updated_at = NOW()
            """,
            fileName,
            originalName,
            contentType,
            Long.valueOf(content.length),
            content
        );
    }

    private Path resolveDiskFile(String fileName) {
        return uploadPath.resolve(fileName).normalize();
    }

    private String normalizeFileName(String fileName) {
        String value = trimToNull(fileName);
        if (value == null) {
            throw new BusinessException("INVALID_FILE_NAME", "Image file name is required");
        }
        String normalized = Paths.get(value).getFileName().toString();
        if (!normalized.equals(value)) {
            throw new BusinessException("INVALID_FILE_NAME", "Invalid image file name");
        }
        return normalized;
    }

    private String normalizeContentType(String contentType, String fileName) {
        String value = trimToNull(contentType);
        if (value != null) {
            return value;
        }
        return guessContentType(fileName);
    }

    private String detectContentType(Path path, String fileName) {
        try {
            String value = Files.probeContentType(path);
            if (value != null && !value.isBlank()) {
                return value;
            }
        } catch (IOException ignored) {
        }
        return guessContentType(fileName);
    }

    private String guessContentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record StoredImageContent(
        String fileName,
        String contentType,
        byte[] content,
        long contentLength
    ) {
    }
}