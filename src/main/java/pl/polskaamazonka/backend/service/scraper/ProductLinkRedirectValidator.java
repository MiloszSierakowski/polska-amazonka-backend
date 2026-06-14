package pl.polskaamazonka.backend.service.scraper;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ProductLinkRedirectValidator {

    private static final Pattern ALIEXPRESS_ITEM_ID = Pattern.compile(
            "/item/(\\d+)\\.html",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALIEXPRESS_ITEM_QUERY = Pattern.compile(
            "[?&]item_id=(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final List<String> NON_PRODUCT_PATH_MARKERS = List.of(
            "/login",
            "/signin",
            "/logowanie",
            "/search",
            "/szukaj",
            "/cart",
            "/koszyk",
            "/checkout",
            "/error",
            "/404",
            "/not-found",
            "/page-not-found",
            "/home",
            "/index"
    );

    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;
    private final AllegroUrlNormalizer allegroUrlNormalizer;

    public ProductLinkRedirectValidator(
            TemuUrlNormalizer temuUrlNormalizer,
            AmazonUrlNormalizer amazonUrlNormalizer,
            AllegroUrlNormalizer allegroUrlNormalizer
    ) {
        this.temuUrlNormalizer = temuUrlNormalizer;
        this.amazonUrlNormalizer = amazonUrlNormalizer;
        this.allegroUrlNormalizer = allegroUrlNormalizer;
    }

    /**
     * Zwraca true, gdy po przekierowaniu wygląda na to, że nie jesteśmy na tym samym produkcie
     * (np. strona główna, logowanie, inny produkt, strona błędu).
     */
    public boolean isSuspiciousRedirect(String originalUrl, String effectiveUrl) {
        if (originalUrl == null || originalUrl.isBlank() || effectiveUrl == null || effectiveUrl.isBlank()) {
            return false;
        }
        String original = normalizeForCompare(originalUrl);
        String effective = normalizeForCompare(effectiveUrl);
        if (original.equalsIgnoreCase(effective)) {
            return false;
        }
        if (pathsEquivalent(original, effective)) {
            return false;
        }
        if (sameProductByPlatformId(originalUrl, effectiveUrl)) {
            return false;
        }
        if (isHomepageOnly(effectiveUrl)) {
            return true;
        }
        if (hasNonProductPath(effectiveUrl)) {
            return true;
        }
        if (!hostsEquivalent(originalUrl, effectiveUrl)) {
            return true;
        }
        if (lostPlatformProductIdentifier(originalUrl, effectiveUrl)) {
            return true;
        }
        if (isShortLinkExpandedToProduct(originalUrl, effectiveUrl)) {
            return false;
        }
        return hasDifferentProductPath(originalUrl, effectiveUrl);
    }

    private String normalizeForCompare(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        if (allegroUrlNormalizer.isAllegroUrl(url)) {
            return allegroUrlNormalizer.normalize(url);
        }
        if (temuUrlNormalizer.isTemuUrl(url)) {
            return temuUrlNormalizer.normalize(url);
        }
        if (amazonUrlNormalizer.isAmazonUrl(url)) {
            return amazonUrlNormalizer.normalize(url);
        }
        return stripTrackingQuery(url.trim());
    }

    private boolean pathsEquivalent(String left, String right) {
        return pathOnly(left).equals(pathOnly(right));
    }

    private boolean sameProductByPlatformId(String originalUrl, String effectiveUrl) {
        if (temuUrlNormalizer.isTemuUrl(originalUrl) || temuUrlNormalizer.isTemuUrl(effectiveUrl)) {
            Long originalId = temuUrlNormalizer.extractGoodsId(originalUrl);
            Long effectiveId = temuUrlNormalizer.extractGoodsId(effectiveUrl);
            return originalId != null && Objects.equals(originalId, effectiveId);
        }
        if (amazonUrlNormalizer.isAmazonUrl(originalUrl) || amazonUrlNormalizer.isAmazonUrl(effectiveUrl)) {
            String originalAsin = amazonUrlNormalizer.extractAsin(originalUrl);
            String effectiveAsin = amazonUrlNormalizer.extractAsin(effectiveUrl);
            return originalAsin != null
                    && effectiveAsin != null
                    && originalAsin.equalsIgnoreCase(effectiveAsin);
        }
        if (allegroUrlNormalizer.isAllegroUrl(originalUrl) || allegroUrlNormalizer.isAllegroUrl(effectiveUrl)) {
            Long originalOffer = allegroUrlNormalizer.extractOfferId(originalUrl);
            Long effectiveOffer = allegroUrlNormalizer.extractOfferId(effectiveUrl);
            return originalOffer != null && Objects.equals(originalOffer, effectiveOffer);
        }
        if (isAliExpressUrl(originalUrl) || isAliExpressUrl(effectiveUrl)) {
            Long originalItem = extractAliExpressItemId(originalUrl);
            Long effectiveItem = extractAliExpressItemId(effectiveUrl);
            return originalItem != null && Objects.equals(originalItem, effectiveItem);
        }
        return false;
    }

    private boolean lostPlatformProductIdentifier(String originalUrl, String effectiveUrl) {
        if (temuUrlNormalizer.isTemuUrl(originalUrl)) {
            Long originalId = temuUrlNormalizer.extractGoodsId(originalUrl);
            Long effectiveId = temuUrlNormalizer.extractGoodsId(effectiveUrl);
            if (originalId != null && effectiveId == null) {
                return true;
            }
            if (originalId != null && effectiveId != null && !Objects.equals(originalId, effectiveId)) {
                return true;
            }
        }
        if (amazonUrlNormalizer.isAmazonUrl(originalUrl)) {
            String originalAsin = amazonUrlNormalizer.extractAsin(originalUrl);
            String effectiveAsin = amazonUrlNormalizer.extractAsin(effectiveUrl);
            if (originalAsin != null && effectiveAsin == null) {
                return true;
            }
            if (originalAsin != null && effectiveAsin != null && !originalAsin.equalsIgnoreCase(effectiveAsin)) {
                return true;
            }
        }
        if (allegroUrlNormalizer.isAllegroUrl(originalUrl)) {
            Long originalOffer = allegroUrlNormalizer.extractOfferId(originalUrl);
            Long effectiveOffer = allegroUrlNormalizer.extractOfferId(effectiveUrl);
            if (originalOffer != null && effectiveOffer == null) {
                return true;
            }
            if (originalOffer != null && effectiveOffer != null && !Objects.equals(originalOffer, effectiveOffer)) {
                return true;
            }
        }
        if (isAliExpressUrl(originalUrl)) {
            Long originalItem = extractAliExpressItemId(originalUrl);
            Long effectiveItem = extractAliExpressItemId(effectiveUrl);
            if (originalItem != null && effectiveItem == null) {
                return true;
            }
            if (originalItem != null && effectiveItem != null && !Objects.equals(originalItem, effectiveItem)) {
                return true;
            }
        }
        return false;
    }

    private boolean isShortLinkExpandedToProduct(String originalUrl, String effectiveUrl) {
        if (!isShortLink(originalUrl)) {
            return false;
        }
        return hasPlatformProductIdentifier(effectiveUrl);
    }

    private boolean hasPlatformProductIdentifier(String url) {
        if (temuUrlNormalizer.extractGoodsId(url) != null) {
            return true;
        }
        if (amazonUrlNormalizer.extractAsin(url) != null) {
            return true;
        }
        if (allegroUrlNormalizer.extractOfferId(url) != null) {
            return true;
        }
        return extractAliExpressItemId(url) != null;
    }

    private boolean hasDifferentProductPath(String originalUrl, String effectiveUrl) {
        String originalPath = pathOnly(originalUrl);
        String effectivePath = pathOnly(effectiveUrl);
        if (originalPath.equals(effectivePath)) {
            return false;
        }
        if (originalPath.length() >= 12 && effectivePath.length() <= 4) {
            return true;
        }
        return originalPath.length() >= 8
                && effectivePath.length() >= 8
                && !originalPath.equals(effectivePath)
                && !hasPlatformProductIdentifier(originalUrl)
                && !hasPlatformProductIdentifier(effectiveUrl);
    }

    private boolean isHomepageOnly(String url) {
        String path = pathOnly(url);
        return "/".equals(path)
                || path.isBlank()
                || "/pl".equals(path)
                || "/en".equals(path)
                || "/pl/".equals(path);
    }

    private boolean hasNonProductPath(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (String marker : NON_PRODUCT_PATH_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean hostsEquivalent(String left, String right) {
        String leftHost = normalizeHost(host(left));
        String rightHost = normalizeHost(host(right));
        return leftHost != null && leftHost.equals(rightHost);
    }

    private boolean isShortLink(String url) {
        String host = normalizeHost(host(url));
        if (host == null) {
            return false;
        }
        return host.equals("amzn.to")
                || host.endsWith(".amzn.to")
                || host.equals("temu.to")
                || host.endsWith(".temu.to");
    }

    private boolean isAliExpressUrl(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host != null && host.toLowerCase(Locale.ROOT).contains("aliexpress.");
        } catch (Exception e) {
            return false;
        }
    }

    private Long extractAliExpressItemId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Matcher queryMatcher = ALIEXPRESS_ITEM_QUERY.matcher(url);
        if (queryMatcher.find()) {
            return parseLong(queryMatcher.group(1));
        }
        Matcher pathMatcher = ALIEXPRESS_ITEM_ID.matcher(url);
        if (pathMatcher.find()) {
            return parseLong(pathMatcher.group(1));
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

    private String stripTrackingQuery(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null) {
                return url;
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            String host = uri.getHost();
            if (host == null) {
                return url;
            }
            return scheme + "://" + host + path;
        } catch (Exception e) {
            return url;
        }
    }

    private String pathOnly(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank()) {
                return "/";
            }
            path = path.replaceAll("/+$", "");
            if (path.isBlank()) {
                return "/";
            }
            return path.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "/";
        }
    }

    private String host(String url) {
        try {
            return URI.create(url.trim()).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.startsWith("www.")) {
            return lower.substring(4);
        }
        return lower;
    }
}
