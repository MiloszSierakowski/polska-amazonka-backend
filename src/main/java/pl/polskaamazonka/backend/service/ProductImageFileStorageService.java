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
public class ProductImageFileStorageService {

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String extension = resolveExtension(file);
        String storedName = UUID.randomUUID() + extension;
        Path target = UploadPaths.resolveProductsDirectory().resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return UploadPaths.PRODUCTS_PUBLIC_PREFIX + storedName;
    }

    private String resolveExtension(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("png")) {
                return ".png";
            }
            if (lower.contains("webp")) {
                return ".webp";
            }
            if (lower.contains("gif")) {
                return ".gif";
            }
        }
        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String lower = originalName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png")) {
                return ".png";
            }
            if (lower.endsWith(".webp")) {
                return ".webp";
            }
            if (lower.endsWith(".gif")) {
                return ".gif";
            }
        }
        return ".jpg";
    }
}
