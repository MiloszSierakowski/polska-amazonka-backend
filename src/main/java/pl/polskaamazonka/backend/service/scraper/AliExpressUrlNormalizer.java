package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AliExpressUrlNormalizer {

    private static final Pattern ITEM_ID_IN_PATH = Pattern.compile(
            "/item/(\\d+)\\.html",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ITEM_ID_IN_QUERY = Pattern.compile(
            "[?&]item_id=(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    public boolean isAliExpressUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("aliexpress.");
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
            if (host == null || !host.toLowerCase(Locale.ROOT).contains("aliexpress.")) {
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

    public Long extractItemId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        Matcher pathMatcher = ITEM_ID_IN_PATH.matcher(trimmed);
        if (pathMatcher.find()) {
            return parseLong(pathMatcher.group(1));
        }
        Matcher queryMatcher = ITEM_ID_IN_QUERY.matcher(trimmed);
        if (queryMatcher.find()) {
            return parseLong(queryMatcher.group(1));
        }
        try {
            URI uri = URI.create(trimmed);
            String path = uri.getPath();
            if (path != null) {
                Matcher pathOnlyMatcher = ITEM_ID_IN_PATH.matcher(path);
                if (pathOnlyMatcher.find()) {
                    return parseLong(pathOnlyMatcher.group(1));
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return null;
        }
    }
}
