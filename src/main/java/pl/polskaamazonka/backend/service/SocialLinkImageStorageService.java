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
public class SocialLinkImageStorageService {

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String extension = resolveExtension(file);
        String storedName = UUID.randomUUID() + extension;
        Path target = UploadPaths.resolveSocialLinksDirectory().resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return UploadPaths.SOCIAL_LINKS_PUBLIC_PREFIX + storedName;
    }

    public void deleteByPublicUrl(String imagePath) {
        if (!UploadPaths.isStoredSocialLinkImageUrl(imagePath)) {
            return;
        }
        String fileName = imagePath.substring(UploadPaths.SOCIAL_LINKS_PUBLIC_PREFIX.length());
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return;
        }
        Path filePath = UploadPaths.resolveSocialLinksDirectory().resolve(fileName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
