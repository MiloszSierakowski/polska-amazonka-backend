package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
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
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductImageStorageService {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final byte[] DEFAULT_PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00,
            0x00, 0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44,
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    private final RestTemplate restTemplate;

    public String tryStoreFromRemoteUrl(String remoteUrl, String referer) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        if (UploadPaths.isStoredProductImageUrl(remoteUrl)) {
            return remoteUrl;
        }
        String stored = storeWithRestTemplate(remoteUrl, referer);
        if (stored != null) {
            return stored;
        }
        return storeWithJsoup(remoteUrl, referer);
    }

    public String storeFromRemoteUrl(String remoteUrl, String referer) {
        String stored = tryStoreFromRemoteUrl(remoteUrl, referer);
        if (stored != null) {
            return stored;
        }
        return ensureDefaultImage();
    }

    public boolean isBrowserDisplayableRemoteUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        if (UploadPaths.isStoredProductImageUrl(imageUrl)) {
            return true;
        }
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    public String ensureDefaultImage() {
        Path target = UploadPaths.resolveProductsDirectory().resolve(UploadPaths.DEFAULT_PRODUCT_IMAGE_FILE_NAME);
        try {
            if (!Files.exists(target)) {
                Files.write(target, DEFAULT_PNG_BYTES);
            }
        } catch (IOException e) {
            return UploadPaths.defaultProductImagePublicUrl();
        }
        return UploadPaths.defaultProductImagePublicUrl();
    }

    public void deleteByPublicUrl(String publicUrl) {
        if (!UploadPaths.isStoredProductImageUrl(publicUrl)) {
            return;
        }
        String fileName = publicUrl.substring(UploadPaths.PRODUCTS_PUBLIC_PREFIX.length());
        if (fileName.isBlank()
                || UploadPaths.DEFAULT_PRODUCT_IMAGE_FILE_NAME.equals(fileName)
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {
            return;
        }
        Path filePath = UploadPaths.resolveProductsDirectory().resolve(fileName);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    private String storeWithRestTemplate(String remoteUrl, String referer) {
        try {
            HttpHeaders headers = buildDownloadHeaders(referer);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    remoteUrl,
                    HttpMethod.GET,
                    request,
                    byte[].class
            );
            return persistBytes(response.getStatusCode().is2xxSuccessful() ? response.getBody() : null, remoteUrl, response.getHeaders().getContentType());
        } catch (Exception e) {
            return null;
        }
    }

    private String storeWithJsoup(String remoteUrl, String referer) {
        try {
            Connection connection = Jsoup.connect(remoteUrl)
                    .userAgent(BROWSER_USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(true)
                    .maxBodySize(0)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7");
            if (referer != null && !referer.isBlank()) {
                connection.referrer(referer);
            }
            Connection.Response response = connection.execute();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return persistBytes(response.bodyAsBytes(), remoteUrl, null);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpHeaders buildDownloadHeaders(String referer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, BROWSER_USER_AGENT);
        headers.set(HttpHeaders.ACCEPT, "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7");
        if (referer != null && !referer.isBlank()) {
            headers.set(HttpHeaders.REFERER, referer);
        }
        return headers;
    }

    private String persistBytes(byte[] body, String remoteUrl, MediaType contentType) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            String extension = resolveExtension(contentType, remoteUrl);
            String storedName = UUID.randomUUID() + extension;
            Path target = UploadPaths.resolveProductsDirectory().resolve(storedName);
            Files.write(target, body);
            return UploadPaths.PRODUCTS_PUBLIC_PREFIX + storedName;
        } catch (Exception e) {
            return null;
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
