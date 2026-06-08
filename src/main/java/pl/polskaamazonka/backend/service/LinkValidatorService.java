package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.polskaamazonka.backend.model.Link;
import pl.polskaamazonka.backend.repository.LinkRepository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LinkValidatorService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_BODY_BYTES = 262_144;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final List<String> BROKEN_CONTENT_MARKERS = List.of(
            "page_not_found_notice",
            "page_not_found_rcmd_title",
            "page_not_found",
            "item-not-found",
            "product-not-found",
            "detail-not-found",
            "przedmiot chwilowo niedostępny",
            "nie znaleziono pożądanej strony",
            "chwilowo niedostępny",
            "page not found",
            "page under maintenance",
            "item is unavailable",
            "item is not available",
            "this item is unavailable",
            "this item is no longer available",
            "product is unavailable",
            "product not found",
            "goods not found",
            "the page you requested cannot be found",
            "sorry, the page you requested cannot be found",
            "ojej, nie możemy znaleźć tej strony",
            "nie możemy znaleźć tej strony",
            "404 not found",
            "404 error",
            "error 404",
            "404 page",
            "http 404",
            "product-not-found",
            "item-not-found",
            "page-not-found",
            "not-found-page",
            "goods-not-found",
            "page-404",
            "item-unavailable",
            "product-unavailable",
            "notfound\":true",
            "notfound\": true",
            "\"notfound\":true",
            "\"status\":404",
            "this item is invalid",
            "item does not exist",
            "product does not exist"
    );

    private final LinkRepository linkRepository;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Transactional
    public void validateAllLinks() {
        List<Link> links = linkRepository.findAll();
        Instant checkedAt = Instant.now();
        for (Link link : links) {
            String url = link.getUrl();
            if (url == null || url.isBlank()) {
                link.setIsBroken(true);
            } else {
                link.setIsBroken(isBroken(url.trim()));
            }
            link.setLastCheckedAt(checkedAt);
        }
        linkRepository.saveAll(links);
    }

    public boolean isBroken(String url) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8");
            applyPlatformHeaders(requestBuilder, url);
            HttpRequest request = requestBuilder.GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode <= 0 || statusCode >= 400) {
                discardBody(response.body());
                return true;
            }
            try (InputStream body = response.body()) {
                String html = readLimitedUtf8(body, MAX_BODY_BYTES);
                return containsBrokenContent(html);
            }
        } catch (IllegalArgumentException | IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return true;
        }
    }

    private boolean containsBrokenContent(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String normalized = html.toLowerCase(Locale.ROOT);
        for (String marker : BROKEN_CONTENT_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        if (normalized.contains("404")) {
            if (normalized.contains("not found")
                    || normalized.contains("unavailable")
                    || normalized.contains("nie możemy znaleźć")
                    || normalized.contains("<title>404")
                    || normalized.contains("error")) {
                return true;
            }
        }
        return false;
    }

    private void applyPlatformHeaders(HttpRequest.Builder requestBuilder, String url) {
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("aliexpress.")) {
            requestBuilder.header("Referer", "https://www.aliexpress.com/");
            return;
        }
        if (lowerUrl.contains("temu.com")) {
            requestBuilder.header("Referer", "https://www.temu.com/");
            return;
        }
        if (lowerUrl.contains("amazon.")) {
            requestBuilder.header("Referer", "https://www.amazon.pl/");
        }
    }

    private String readLimitedUtf8(InputStream inputStream, int maxBytes) throws IOException {
        byte[] buffer = new byte[maxBytes];
        int totalRead = 0;
        int read;
        while (totalRead < maxBytes && (read = inputStream.read(buffer, totalRead, maxBytes - totalRead)) != -1) {
            totalRead += read;
        }
        discardBody(inputStream);
        if (totalRead == 0) {
            return "";
        }
        return new String(buffer, 0, totalRead, StandardCharsets.UTF_8);
    }

    private void discardBody(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            byte[] skipBuffer = new byte[8192];
            while (inputStream.read(skipBuffer) != -1) {
            }
        } catch (IOException ignored) {
        }
    }
}
