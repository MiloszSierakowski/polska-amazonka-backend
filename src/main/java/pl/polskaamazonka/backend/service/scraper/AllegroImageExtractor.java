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
public class AllegroImageExtractor {

    private static final Pattern ALLEGRO_IMAGE_URL = Pattern.compile(
            "https://a\\.allegroimg\\.com/[^\"'\\s<>\\\\]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ORIGINAL_SEGMENT = Pattern.compile("/original/", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String extract(Document document) {
        Set<String> candidates = new LinkedHashSet<>();
        collectMetaCandidates(document, candidates);
        collectDomCandidates(document, candidates);
        collectHtmlCandidates(document.html(), candidates);
        collectNextDataCandidates(document, candidates);
        return pickBest(candidates);
    }

    private void collectMetaCandidates(Document document, Set<String> candidates) {
        addCandidate(candidates, metaContent(document, "og:image"));
        addCandidate(candidates, metaContent(document, "og:image:secure_url"));
        addCandidate(candidates, metaContent(document, "twitter:image"));
    }

    private void collectDomCandidates(Document document, Set<String> candidates) {
        Elements images = document.select(
                "img[data-src], img[data-srcset], img[src], img[fetchpriority=high], img[data-testid=product-image]"
        );
        for (Element image : images) {
            addCandidate(candidates, image.attr("data-src"));
            addCandidate(candidates, image.attr("src"));
            addSrcsetCandidates(candidates, image.attr("data-srcset"));
            addSrcsetCandidates(candidates, image.attr("srcset"));
        }
    }

    private void collectHtmlCandidates(String html, Set<String> candidates) {
        if (html == null || html.isBlank()) {
            return;
        }
        Matcher matcher = ALLEGRO_IMAGE_URL.matcher(html);
        while (matcher.find()) {
            addCandidate(candidates, matcher.group());
        }
    }

    private void collectNextDataCandidates(Document document, Set<String> candidates) {
        Element script = document.getElementById("__NEXT_DATA__");
        if (script == null) {
            return;
        }
        String json = script.html();
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            collectJsonImageUrls(root, candidates);
        } catch (Exception ignored) {
        }
    }

    private void collectJsonImageUrls(JsonNode node, Set<String> candidates) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addCandidate(candidates, node.asText());
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectJsonImageUrls(child, candidates);
            }
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> collectJsonImageUrls(entry.getValue(), candidates));
        }
    }

    private void addSrcsetCandidates(Set<String> candidates, String srcset) {
        if (srcset == null || srcset.isBlank()) {
            return;
        }
        String[] parts = srcset.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int spaceIndex = trimmed.indexOf(' ');
            String url = spaceIndex > 0 ? trimmed.substring(0, spaceIndex).trim() : trimmed;
            addCandidate(candidates, url);
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
        String trimmed = value.trim();
        if (!isAllegroImageUrl(trimmed)) {
            return;
        }
        if (trimmed.contains("placeholder") || trimmed.endsWith(".svg")) {
            return;
        }
        candidates.add(trimmed);
    }

    private boolean isAllegroImageUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://a.allegroimg.com/")
                || lower.startsWith("http://a.allegroimg.com/");
    }

    private String pickBest(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((String url) -> ORIGINAL_SEGMENT.matcher(url).find() ? 0 : 1)
                .thenComparingInt(url -> sizeScore(url))
                .thenComparingInt(String::length).reversed());
        return sorted.get(0);
    }

    private int sizeScore(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("/original/")) {
            return 0;
        }
        if (lower.contains("/s1024/")) {
            return 1;
        }
        if (lower.contains("/s720/")) {
            return 2;
        }
        if (lower.contains("/s360/")) {
            return 3;
        }
        if (lower.contains("/s180/")) {
            return 4;
        }
        return 5;
    }
}
