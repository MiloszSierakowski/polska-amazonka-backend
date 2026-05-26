package pl.polskaamazonka.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.polskaamazonka.backend.config.UploadPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class CategoryFileStorageService {

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String extension = resolveExtension(file);
        String storedName = UUID.randomUUID() + extension;
        Path target = UploadPaths.resolveCategoriesDirectory().resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return UploadPaths.CATEGORIES_PUBLIC_PREFIX + storedName;
    }

    public void deleteByPublicUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String fileName = extractFileName(imageUrl);
        if (fileName == null) {
            return;
        }
        Path filePath = UploadPaths.resolveCategoriesDirectory().resolve(fileName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extractFileName(String imageUrl) {
        String fileName = null;
        if (imageUrl.startsWith(UploadPaths.CATEGORIES_PUBLIC_PREFIX)) {
            fileName = imageUrl.substring(UploadPaths.CATEGORIES_PUBLIC_PREFIX.length());
        } else if (imageUrl.startsWith(UploadPaths.LEGACY_CATEGORIES_PUBLIC_PREFIX)) {
            fileName = imageUrl.substring(UploadPaths.LEGACY_CATEGORIES_PUBLIC_PREFIX.length());
        }
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }
        return fileName;
    }

    private String resolveExtension(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (ext.matches("\\.(png|jpg|jpeg|gif|webp|bmp|svg)")) {
                return ext;
            }
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/png" -> ".png";
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                case "image/bmp" -> ".bmp";
                case "image/svg+xml" -> ".svg";
                default -> ".png";
            };
        }
        return ".png";
    }
}
