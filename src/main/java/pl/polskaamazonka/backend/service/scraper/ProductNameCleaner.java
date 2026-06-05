package pl.polskaamazonka.backend.service.scraper;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ProductNameCleaner {

    private static final int MAX_NAME_LENGTH = 80;
    private static final String FALLBACK_NAME = "Produkt bez nazwy";

    private static final Pattern TRIPLE_EXCLAMATION = Pattern.compile("!{3,}");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern SPECIAL_CHARS = Pattern.compile("[*•]+");
    private static final Pattern MARKETING_SUFFIX = Pattern.compile(
            "(?i)\\s*[-|–—]\\s*(kupuj tanio na aliexpress|aliexpress|allegro(\\.pl)?|amazon(\\.pl)?|temu(\\s+poland)?|shopee|ebay(\\.pl)?|ceneo(\\.pl)?|empik(\\.com)?|media expert|x-kom|morele(\\.net)?|sklep internetowy[^|•–—-]*).*"
    );
    private static final Pattern TEMU_TITLE_SUFFIX = Pattern.compile("(?i)\\s*[-|–—]\\s*temu(\\s+poland)?\\s*$");
    private static final Pattern AMAZON_TITLE_SUFFIX = Pattern.compile("(?i)\\s*:\\s*amazon(\\.[a-z]{2,3})?(\\s*[^:]*?)?\\s*$");
    private static final Pattern PIPE_SUFFIX = Pattern.compile("\\s*\\|.+$");
    private static final Pattern DASH_SUFFIX = Pattern.compile("\\s+[-–—]\\s+[^-–—|•]{0,60}(sklep|shop|store|marketplace|internetowy).*$", Pattern.CASE_INSENSITIVE);

    public String clean(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = Jsoup.parse(raw).text().trim();
        name = MARKETING_SUFFIX.matcher(name).replaceAll("");
        name = TEMU_TITLE_SUFFIX.matcher(name).replaceAll("");
        name = AMAZON_TITLE_SUFFIX.matcher(name).replaceAll("");
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

    public String nameFromUrlSlug(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                return null;
            }
            String[] segments = path.split("/");
            String segment = null;
            for (int i = segments.length - 1; i >= 0; i--) {
                String candidate = segments[i];
                if (candidate != null && !candidate.isBlank() && !isReservedPathSegment(candidate)) {
                    segment = candidate;
                    break;
                }
            }
            if (segment == null || segment.isBlank()) {
                return null;
            }
            if (segment.contains("?")) {
                segment = segment.substring(0, segment.indexOf('?'));
            }
            segment = URLDecoder.decode(segment, StandardCharsets.UTF_8);
            segment = segment.replace('-', ' ').replace('_', ' ');
            segment = EXTRA_SPACES.matcher(segment).replaceAll(" ").trim();
            if (segment.isBlank() || segment.chars().allMatch(Character::isDigit)) {
                return null;
            }
            String cleaned = clean(segment);
            if (cleaned != null && !cleaned.isBlank()) {
                return cleaned;
            }
            if (segment.length() > MAX_NAME_LENGTH) {
                segment = shortenAtLogicalBreak(segment, MAX_NAME_LENGTH);
            }
            return segment;
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean isWeakScrapedName(String name, String pageUrl) {
        if (name == null || name.isBlank() || FALLBACK_NAME.equals(name)) {
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.length() < 3) {
            return true;
        }
        try {
            URI uri = URI.create(pageUrl);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.startsWith("www.")) {
                normalizedHost = normalizedHost.substring(4);
            }
            String lowerName = trimmed.toLowerCase(Locale.ROOT);
            if (lowerName.equals(normalizedHost) || lowerName.equals(host.toLowerCase(Locale.ROOT))) {
                return true;
            }
            if (lowerName.endsWith(".pl") && !lowerName.contains(" ") && lowerName.length() < 20) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public String fallbackFromUrl(String pageUrl) {
        String slugName = nameFromUrlSlug(pageUrl);
        if (slugName != null && !slugName.isBlank()) {
            return slugName;
        }
        try {
            URI uri = URI.create(pageUrl);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_NAME;
    }

    private boolean isReservedPathSegment(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        return lower.equals("produkt")
                || lower.equals("oferta")
                || lower.equals("item")
                || lower.equals("p")
                || lower.equals("dp")
                || lower.equals("pl");
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
}
