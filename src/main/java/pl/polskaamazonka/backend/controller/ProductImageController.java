package pl.polskaamazonka.backend.controller;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductImageController {

    private static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "a.allegroimg.com",
            "assets.allegrostatic.com"
    );

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String trimmedUrl = url.trim();
        validateAllowedHost(trimmedUrl);
        try {
            Connection.Response response = Jsoup.connect(trimmedUrl)
                    .userAgent(BROWSER_USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .ignoreContentType(true)
                    .ignoreHttpErrors(false)
                    .maxBodySize(0)
                    .referrer("https://allegro.pl/")
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("Accept-Language", "pl-PL,pl;q=0.9,en-US;q=0.8,en;q=0.7")
                    .execute();
            byte[] body = response.bodyAsBytes();
            if (body == null || body.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
            }
            MediaType mediaType = resolveMediaType(response.contentType(), trimmedUrl);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentType(mediaType)
                    .body(body);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);
        }
    }

    private void validateAllowedHost(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            String host = uri.getHost();
            if (host == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (!ALLOWED_HOSTS.contains(normalizedHost)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private MediaType resolveMediaType(String contentType, String imageUrl) {
        if (contentType != null && !contentType.isBlank()) {
            try {
                return MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
            }
        }
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.contains(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.contains(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_JPEG;
    }
}
