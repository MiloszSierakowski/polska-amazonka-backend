package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AllegroUrlNormalizer {

    private static final Pattern OFFER_ID_SUFFIX = Pattern.compile("-([0-9]{6,})$");

    public boolean isAllegroUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("allegro.");
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
            if (host == null || !host.toLowerCase(Locale.ROOT).contains("allegro.")) {
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

    public Long extractOfferId(String url) {
        String normalized = normalize(url);
        try {
            URI uri = URI.create(normalized);
            String path = uri.getPath();
            if (path == null) {
                return null;
            }
            String segment = path.substring(path.lastIndexOf('/') + 1);
            Matcher matcher = OFFER_ID_SUFFIX.matcher(segment);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
