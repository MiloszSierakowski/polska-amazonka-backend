package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonUrlNormalizer {

    private static final Pattern ASIN_IN_PATH = Pattern.compile(
            "/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})(?:[/?]|$)",
            Pattern.CASE_INSENSITIVE
    );

    public boolean isAmazonUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return false;
            }
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.contains("amazon.") || lower.equals("amzn.to") || lower.endsWith(".amzn.to");
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
            if (host == null) {
                return url.trim();
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            if (lowerHost.equals("amzn.to") || lowerHost.endsWith(".amzn.to")) {
                return url.trim();
            }
            if (!lowerHost.contains("amazon.")) {
                return url.trim();
            }
            String asin = extractAsinFromPath(uri.getPath());
            if (asin == null) {
                String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
                String path = uri.getPath() == null ? "" : uri.getPath();
                return scheme + "://" + host + path;
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            return scheme + "://" + host + "/dp/" + asin;
        } catch (Exception e) {
            return url.trim();
        }
    }

    public String extractAsin(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            return extractAsinFromPath(uri.getPath());
        } catch (Exception e) {
            Matcher matcher = ASIN_IN_PATH.matcher(url);
            if (matcher.find()) {
                return matcher.group(1).toUpperCase(Locale.ROOT);
            }
            return null;
        }
    }

    public String refererFor(String url) {
        if (url == null || url.isBlank()) {
            return "https://www.amazon.pl/";
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return "https://www.amazon.pl/";
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            return scheme + "://" + host + "/";
        } catch (Exception e) {
            return "https://www.amazon.pl/";
        }
    }

    private String extractAsinFromPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Matcher matcher = ASIN_IN_PATH.matcher(path);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }
}
