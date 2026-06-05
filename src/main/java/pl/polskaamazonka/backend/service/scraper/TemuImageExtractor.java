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
public class TemuImageExtractor {

    private static final int CHALLENGE_PAGE_MAX_LENGTH = 20000;
    private static final Pattern KWCDN_PRODUCT_URL = Pattern.compile(
            "https://img[a-z0-9.-]*\\.kwcdn\\.com/product/[^\"'\\s<>\\\\]+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern KWCDN_IMAGE_URL = Pattern.compile(
            "https://img[a-z0-9.-]*\\.kwcdn\\.com/[^\"'\\s<>\\\\]+\\.(?:jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern JSON_IMAGE_FIELD = Pattern.compile(
            "\"(?:hdThumbUrl|thumbUrl|imageUrl|mainImageUrl|coverUrl|url)\"\\s*:\\s*\"(https://img[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isChallengePage(Document document) {
        if (document == null) {
            return true;
        }
        String html = document.html();
        if (html == null || html.length() < CHALLENGE_PAGE_MAX_LENGTH) {
            return true;
        }
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("please enable javascript")
                || lower.contains("enable js")
                || lower.contains("verify you are human");
    }

    public String extract(Document document) {
        Set<String> candidates = new LinkedHashSet<>();
        collectMetaCandidates(document, candidates);
        collectDomCandidates(document, candidates);
        String html = document.html();
        collectHtmlCandidates(html, candidates);
        collectJsonCandidates(html, candidates);
        return pickBest(candidates);
    }

    private void collectMetaCandidates(Document document, Set<String> candidates) {
        addCandidate(candidates, metaContent(document, "og:image"));
        addCandidate(candidates, metaContent(document, "og:image:secure_url"));
        addCandidate(candidates, metaContent(document, "twitter:image"));
    }

    private void collectDomCandidates(Document document, Set<String> candidates) {
        Elements images = document.select("img[data-tooltip-title], img[data-src], img[src]");
        for (Element image : images) {
            addCandidate(candidates, image.attr("data-src"));
            addCandidate(candidates, image.attr("src"));
        }
    }

    private void collectHtmlCandidates(String html, Set<String> candidates) {
        if (html == null || html.isBlank()) {
            return;
        }
        collectPatternMatches(KWCDN_PRODUCT_URL, html, candidates);
        collectPatternMatches(KWCDN_IMAGE_URL, html, candidates);
    }

    private void collectJsonCandidates(String html, Set<String> candidates) {
        if (html == null || html.isBlank()) {
            return;
        }
        Matcher matcher = JSON_IMAGE_FIELD.matcher(html);
        while (matcher.find()) {
            addCandidate(candidates, matcher.group(1));
        }
        for (String marker : List.of("window.__InitialI18nStore__", "window.__MONITOR_INFOS__")) {
            int start = html.indexOf(marker);
            if (start < 0) {
                continue;
            }
            int jsonStart = html.indexOf('{', start);
            if (jsonStart < 0) {
                continue;
            }
            String json = extractBalancedJson(html, jsonStart);
            if (json == null) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(json);
                collectJsonImageUrls(root, candidates);
            } catch (Exception ignored) {
            }
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

    private void collectPatternMatches(Pattern pattern, String html, Set<String> candidates) {
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            addCandidate(candidates, matcher.group());
        }
    }

    private String extractBalancedJson(String html, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < html.length(); i++) {
            char ch = html.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return html.substring(start, i + 1);
                }
            }
        }
        return null;
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
        if (!isTemuImageUrl(trimmed)) {
            return;
        }
        if (trimmed.contains("upload_aimg") || trimmed.contains("/m-assets/")) {
            return;
        }
        candidates.add(trimmed);
    }

    private boolean isTemuImageUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://img")
                && lower.contains("kwcdn.com")
                && !lower.endsWith(".svg");
    }

    private String pickBest(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((String url) -> url.toLowerCase(Locale.ROOT).contains("/product/") ? 0 : 1)
                .thenComparingInt(String::length).reversed());
        return sorted.get(0);
    }
}
