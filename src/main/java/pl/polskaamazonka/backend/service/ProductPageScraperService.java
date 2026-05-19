package pl.polskaamazonka.backend.service;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProductPageScraperService {

    private static final String USER_AGENT = "PolskaAmazonka/1.0";
    private static final int TIMEOUT_MS = 12000;
    private static final int MAX_NAME_LENGTH = 80;
    private static final String FALLBACK_NAME = "Produkt bez nazwy";

    private static final Pattern TRIPLE_EXCLAMATION = Pattern.compile("!{3,}");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[*•]+");
    private static final Pattern MARKETING_SUFFIX = Pattern.compile(
            "(?i)\\s*[-|–—]\\s*(kupuj tanio na aliexpress|aliexpress|allegro(\\.pl)?|amazon(\\.pl)?|temu|shopee|ebay(\\.pl)?|ceneo(\\.pl)?|empik(\\.com)?|media expert|x-kom|morele(\\.net)?|sklep internetowy[^|•–—-]*).*"
    );
    private static final Pattern PIPE_SUFFIX = Pattern.compile("\\s*\\|.+$");
    private static final Pattern DASH_SUFFIX = Pattern.compile("\\s+[-–—]\\s+[^-–—|•]{0,60}(sklep|shop|store|marketplace|internetowy).*$", Pattern.CASE_INSENSITIVE);

    public ProductPageData scrape(String pageUrl) {
        try {
            Document document = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();
            String rawTitle = extractRawTitle(document);
            String cleanedName = cleanProductName(rawTitle);
            if (cleanedName == null || cleanedName.isBlank()) {
                cleanedName = fallbackNameFromUrl(pageUrl);
            }
            String imageUrl = extractImageUrl(document);
            return new ProductPageData(cleanedName, imageUrl);
        } catch (Exception e) {
            return new ProductPageData(fallbackNameFromUrl(pageUrl), null);
        }
    }

    public String resolveProductName(String pageUrl, String providedName) {
        if (providedName != null && !providedName.isBlank()) {
            String cleaned = cleanProductName(providedName);
            if (cleaned != null && !cleaned.isBlank()) {
                return cleaned;
            }
        }
        return scrape(pageUrl).getName();
    }

    private String extractRawTitle(Document document) {
        String title = metaContent(document, "og:title", true);
        if (title == null || title.isBlank()) {
            title = metaContent(document, "twitter:title", false);
        }
        if (title == null || title.isBlank()) {
            title = metaContent(document, "twitter:title", true);
        }
        if (title == null || title.isBlank()) {
            title = document.title();
        }
        return title;
    }

    private String extractImageUrl(Document document) {
        String imageUrl = metaContent(document, "og:image", true);
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "twitter:image", false);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            imageUrl = metaContent(document, "twitter:image", true);
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            Elements links = document.select("link[rel=image_src]");
            for (Element link : links) {
                String href = link.attr("href");
                if (href != null && !href.isBlank()) {
                    imageUrl = href;
                    break;
                }
            }
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return imageUrl.trim();
    }

    private String metaContent(Document document, String key, boolean property) {
        String selector = property
                ? String.format("meta[property=%s]", key)
                : String.format("meta[name=%s]", key);
        Elements elements = document.select(selector);
        for (Element element : elements) {
            String content = element.attr("content");
            if (content != null && !content.isBlank()) {
                return content.trim();
            }
        }
        return null;
    }

    private String cleanProductName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = Jsoup.parse(raw).text().trim();
        name = MARKETING_SUFFIX.matcher(name).replaceAll("");
        name = PIPE_SUFFIX.matcher(name).replaceAll("");
        name = DASH_SUFFIX.matcher(name).replaceAll("");
        name = SPECIAL_CHARS.matcher(name).replaceAll(" ");
        name = TRIPLE_EXCLAMATION.matcher(name).replaceAll("");
        name = EXTRA_SPACES.matcher(name).replaceAll(" ").trim();
        if (name.isBlank()) {
            return null;
        }
        if (isAllUppercaseLetters(name)) {
            name = toTitleCase(name);
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = shortenAtLogicalBreak(name, MAX_NAME_LENGTH);
        }
        name = EXTRA_SPACES.matcher(name).replaceAll(" ").trim();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH).trim();
        }
        return name.isBlank() ? null : name;
    }

    private String shortenAtLogicalBreak(String value, int maxLength) {
        int comma = value.indexOf(',', 0);
        int dot = value.indexOf('.', 0);
        int semicolon = value.indexOf(';', 0);
        int breakIndex = -1;
        if (comma > 0 && comma < maxLength) {
            breakIndex = comma;
        }
        if (dot > 0 && dot < maxLength && (breakIndex < 0 || dot < breakIndex)) {
            breakIndex = dot;
        }
        if (semicolon > 0 && semicolon < maxLength && (breakIndex < 0 || semicolon < breakIndex)) {
            breakIndex = semicolon;
        }
        if (breakIndex > 0) {
            return value.substring(0, breakIndex).trim();
        }
        int lastSpace = value.lastIndexOf(' ', maxLength);
        if (lastSpace > 20) {
            return value.substring(0, lastSpace).trim();
        }
        return value.substring(0, maxLength).trim();
    }

    private boolean isAllUppercaseLetters(String value) {
        boolean hasLetter = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
                if (Character.isLowerCase(ch)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private String toTitleCase(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(lower.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetter(ch)) {
                builder.append(capitalizeNext ? Character.toUpperCase(ch) : ch);
                capitalizeNext = false;
            } else {
                builder.append(ch);
                capitalizeNext = !Character.isLetterOrDigit(ch);
            }
        }
        return builder.toString().trim();
    }

    private String fallbackNameFromUrl(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl);
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                String segment = path.substring(path.lastIndexOf('/') + 1);
                if (segment.contains("?")) {
                    segment = segment.substring(0, segment.indexOf('?'));
                }
                segment = URLDecoder.decode(segment, StandardCharsets.UTF_8);
                segment = segment.replace('-', ' ').replace('_', ' ');
                segment = EXTRA_SPACES.matcher(segment).replaceAll(" ").trim();
                if (!segment.isBlank()) {
                    String cleaned = cleanProductName(segment);
                    if (cleaned != null && !cleaned.isBlank()) {
                        return cleaned;
                    }
                    if (segment.length() > MAX_NAME_LENGTH) {
                        segment = shortenAtLogicalBreak(segment, MAX_NAME_LENGTH);
                    }
                    return segment;
                }
            }
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_NAME;
    }

    @Getter
    public static class ProductPageData {
        private final String name;
        private final String imageUrl;

        public ProductPageData(String name, String imageUrl) {
            this.name = name;
            this.imageUrl = imageUrl;
        }
    }
}
