package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.polskaamazonka.backend.config.UploadPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoThumbnailStorageService {

    private static final byte[] DEFAULT_PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00,
            0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private final RestTemplate restTemplate;

    public String storeFromRemoteUrl(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return ensureDefaultThumbnail();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "PolskaAmazonka/1.0");
            headers.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    remoteUrl,
                    HttpMethod.GET,
                    request,
                    byte[].class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().length == 0) {
                return ensureDefaultThumbnail();
            }
            String extension = resolveExtension(response.getHeaders().getContentType(), remoteUrl);
            String storedName = UUID.randomUUID() + extension;
            Path target = UploadPaths.resolveVideosDirectory().resolve(storedName);
            Files.write(target, response.getBody());
            return UploadPaths.VIDEOS_PUBLIC_PREFIX + storedName;
        } catch (Exception e) {
            return ensureDefaultThumbnail();
        }
    }

    public String ensureDefaultThumbnail() {
        Path target = UploadPaths.resolveVideosDirectory().resolve(UploadPaths.DEFAULT_VIDEO_THUMB_FILE_NAME);
        try {
            if (!Files.exists(target)) {
                Files.write(target, DEFAULT_PNG_BYTES);
            }
        } catch (IOException e) {
            return UploadPaths.defaultVideoThumbnailPublicUrl();
        }
        return UploadPaths.defaultVideoThumbnailPublicUrl();
    }

    public boolean isReadableStoredUrl(String publicUrl) {
        if (!UploadPaths.isStoredVideoThumbnailUrl(publicUrl)) {
            return false;
        }
        String fileName = publicUrl.substring(UploadPaths.VIDEOS_PUBLIC_PREFIX.length());
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        Path filePath = UploadPaths.resolveVideosDirectory().resolve(fileName);
        try {
            return Files.isRegularFile(filePath) && Files.size(filePath) > 0L;
        } catch (IOException e) {
            return false;
        }
    }

    public void deleteByPublicUrl(String publicUrl) {
        if (!UploadPaths.isStoredVideoThumbnailUrl(publicUrl)) {
            return;
        }
        String fileName = publicUrl.substring(UploadPaths.VIDEOS_PUBLIC_PREFIX.length());
        if (fileName.isBlank()
                || UploadPaths.DEFAULT_VIDEO_THUMB_FILE_NAME.equals(fileName)
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {
            return;
        }
        Path filePath = UploadPaths.resolveVideosDirectory().resolve(fileName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    private String resolveExtension(MediaType contentType, String remoteUrl) {
        if (contentType != null) {
            if (MediaType.IMAGE_JPEG.includes(contentType)) {
                return ".jpg";
            }
            if (MediaType.IMAGE_PNG.includes(contentType)) {
                return ".png";
            }
            if (MediaType.IMAGE_GIF.includes(contentType)) {
                return ".gif";
            }
            if (MediaType.parseMediaType("image/webp").includes(contentType)) {
                return ".webp";
            }
        }
        String lower = remoteUrl.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) {
            return ".png";
        }
        if (lower.contains(".webp")) {
            return ".webp";
        }
        if (lower.contains(".gif")) {
            return ".gif";
        }
        return ".jpg";
    }
}
