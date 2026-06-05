package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemuUrlNormalizer {

    private static final Pattern GOODS_ID_IN_PATH = Pattern.compile("-g-(\\d{8,})(?:\\.html)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS_ID_IN_QUERY = Pattern.compile("[?&]goods_id=(\\d{8,})", Pattern.CASE_INSENSITIVE);

    public boolean isTemuUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("temu.com");
        } catch (Exception e) {
            return false;
        }
    }

    public String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || !host.toLowerCase(Locale.ROOT).contains("temu.com")) {
                return url.trim();
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return url.trim();
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            return scheme + "://" + host + path;
        } catch (Exception e) {
            return url.trim();
        }
    }

    public Long extractGoodsId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        Matcher queryMatcher = GOODS_ID_IN_QUERY.matcher(trimmed);
        if (queryMatcher.find()) {
            return parseGoodsId(queryMatcher.group(1));
        }
        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String segment = path.substring(path.lastIndexOf('/') + 1);
            Matcher pathMatcher = GOODS_ID_IN_PATH.matcher(segment);
            if (pathMatcher.find()) {
                return parseGoodsId(pathMatcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public String nameFromTemuSlug(String pageUrl) {
        try {
            URI uri = URI.create(pageUrl);
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String segment = path.substring(path.lastIndexOf('/') + 1);
            if (segment.contains("?")) {
                segment = segment.substring(0, segment.indexOf('?'));
            }
            segment = segment.replaceAll("(?i)-g-\\d+(?:\\.html)?$", "");
            segment = segment.replaceAll("(?i)\\.html$", "");
            segment = segment.replace('-', ' ').replace('_', ' ');
            segment = segment.replaceAll("\\s{2,}", " ").trim();
            return segment.isBlank() ? null : segment;
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseGoodsId(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}
