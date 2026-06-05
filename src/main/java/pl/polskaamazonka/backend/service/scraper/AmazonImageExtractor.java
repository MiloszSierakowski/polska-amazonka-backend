package pl.polskaamazonka.backend.service.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonImageExtractor {

    private static final Pattern AMAZON_IMAGE_URL = Pattern.compile(
            "https://m\\.media-amazon\\.com/images/I/[A-Za-z0-9+%._-]+\\.(?:jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_IMAGE_FIELD = Pattern.compile(
            "\"(?:hiRes|large|thumb|mainUrl|url)\"\\s*:\\s*\"(https://m\\.media-amazon\\.com/images/I/[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extract(Document document) {
        Set<String> domCandidates = new LinkedHashSet<>();
        collectDomCandidates(document, domCandidates);
        String fromDom = pickBest(domCandidates);
        if (fromDom != null) {
            return fromDom;
        }
        Set<String> candidates = new LinkedHashSet<>();
        collectMetaCandidates(document, candidates);
        collectHtmlCandidates(document.html(), candidates);
        return pickBest(candidates);
    }

    private void collectMetaCandidates(Document document, Set<String> candidates) {
        addCandidate(candidates, metaContent(document, "og:image"));
        addCandidate(candidates, metaContent(document, "og:image:secure_url"));
        addCandidate(candidates, metaContent(document, "twitter:image"));
    }

    private void collectDomCandidates(Document document, Set<String> candidates) {
        Elements images = document.select(
                "#landingImage, #imgBlkFront, img[data-old-hires], img[data-a-dynamic-image], img[src*='media-amazon.com/images/I/']"
        );
        for (Element image : images) {
            addCandidate(candidates, image.attr("data-old-hires"));
            addCandidate(candidates, image.attr("src"));
            collectDynamicImageCandidates(candidates, image.attr("data-a-dynamic-image"));
        }
    }

    private void collectDynamicImageCandidates(Set<String> candidates, String dynamicImageJson) {
        if (dynamicImageJson == null || dynamicImageJson.isBlank()) {
            return;
        }
        String decoded = dynamicImageJson
                .replace("&quot;", "\"")
                .replace("&#34;", "\"");
        try {
            JsonNode root = objectMapper.readTree(decoded);
            if (root.isObject()) {
                root.fieldNames().forEachRemaining(field -> addCandidate(candidates, field));
            }
        } catch (Exception ignored) {
            Matcher matcher = AMAZON_IMAGE_URL.matcher(decoded);
            while (matcher.find()) {
                addCandidate(candidates, matcher.group());
            }
        }
    }

    private void collectHtmlCandidates(String html, Set<String> candidates) {
        if (html == null || html.isBlank()) {
            return;
        }
        Matcher imageMatcher = AMAZON_IMAGE_URL.matcher(html);
        while (imageMatcher.find()) {
            addCandidate(candidates, imageMatcher.group());
        }
        Matcher jsonMatcher = JSON_IMAGE_FIELD.matcher(html);
        while (jsonMatcher.find()) {
            addCandidate(candidates, jsonMatcher.group(1));
        }
    }

    private String metaContent(Document document, String key) {
        Element element = document.selectFirst("meta[property=" + key + "]");
        if (element == null) {
            element = document.selectFirst("meta[name=" + key + "]");
        }
        if (element == null) {
            return null;
        }
        return element.attr("content");
    }

    private void addCandidate(Set<String> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String sanitized = sanitizeImageUrl(value);
        if (sanitized == null || !isAmazonProductImageUrl(sanitized)) {
            return;
        }
        candidates.add(sanitized);
    }

    private String sanitizeImageUrl(String value) {
        String trimmed = value.trim()
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&amp;", "&");
        int quoteIndex = trimmed.indexOf('"');
        if (quoteIndex > 0) {
            trimmed = trimmed.substring(0, quoteIndex);
        }
        Matcher matcher = AMAZON_IMAGE_URL.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private boolean isAmazonProductImageUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://m.media-amazon.com/images/i/")
                && !lower.contains("/share-icons/")
                && !lower.contains("previewdoh")
                && !lower.contains("pkdp-play-icon-overlay")
                && !lower.contains("community-review")
                && !lower.endsWith(".svg")
                && !lower.endsWith(".gif");
    }

    private String pickBest(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(this::score));
        return sorted.get(0);
    }

    private int score(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("_ac_sl1500_") || lower.contains("_sl1500_")) {
            return 0;
        }
        if (lower.contains("_ac_sl1000_") || lower.contains("_sl1000_")) {
            return 1;
        }
        if (lower.contains("_ac_sl500_") || lower.contains("_sl500_")) {
            return 2;
        }
        if (lower.contains(".jpg_bo30")) {
            return 3;
        }
        if (lower.contains("_sy500_") || lower.contains("_sx") || lower.contains("_sy") || lower.contains("_sr")) {
            return 6;
        }
        if (lower.contains("_ac_us")) {
            return 5;
        }
        return 4;
    }
}
