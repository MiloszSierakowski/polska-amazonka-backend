package pl.polskaamazonka.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductImageScraperService {

    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OG_IMAGE_REVERSE_PATTERN = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWITTER_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWITTER_IMAGE_REVERSE_PATTERN = Pattern.compile(
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+name=[\"']twitter:image[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TWITTER_IMAGE_PROPERTY_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMAGE_SRC_PATTERN = Pattern.compile(
            "<link[^>]+rel=[\"']image_src[\"'][^>]+href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern IMAGE_SRC_REVERSE_PATTERN = Pattern.compile(
            "<link[^>]+href=[\"']([^\"']+)[\"'][^>]+rel=[\"']image_src[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;

    public String scrapeImageUrl(String pageUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "PolskaAmazonka/1.0");
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(URI.create(pageUrl), HttpMethod.GET, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            String html = response.getBody();
            String imageUrl = firstMatch(html, OG_IMAGE_PATTERN);
            if (imageUrl == null) {
                imageUrl = firstMatch(html, OG_IMAGE_REVERSE_PATTERN);
            }
            if (imageUrl == null) {
                imageUrl = firstMatch(html, TWITTER_IMAGE_PATTERN);
            }
            if (imageUrl == null) {
                imageUrl = firstMatch(html, TWITTER_IMAGE_REVERSE_PATTERN);
            }
            if (imageUrl == null) {
                imageUrl = firstMatch(html, TWITTER_IMAGE_PROPERTY_PATTERN);
            }
            if (imageUrl == null) {
                imageUrl = firstMatch(html, IMAGE_SRC_PATTERN);
            }
            if (imageUrl == null) {
                imageUrl = firstMatch(html, IMAGE_SRC_REVERSE_PATTERN);
            }
            if (imageUrl == null || imageUrl.isBlank()) {
                return null;
            }
            return imageUrl.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstMatch(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
