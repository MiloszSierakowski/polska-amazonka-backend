package pl.polskaamazonka.backend.service;

import org.springframework.stereotype.Component;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AffiliateTrackingDetector {

    private static final Set<String> ALIEXPRESS_AFFILIATE_PARAMS = Set.of(
            "aff_fsk",
            "aff_platform",
            "aff_fcid",
            "aff_trace_key",
            "aff_short_key",
            "terminal_id"
    );

    private final AffiliateShortLinkResolver affiliateShortLinkResolver;
    private final AllegroUrlNormalizer allegroUrlNormalizer;
    private final AliExpressUrlNormalizer aliExpressUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;
    private final AmazonUrlNormalizer amazonUrlNormalizer;

    public AffiliateTrackingDetector(
            AffiliateShortLinkResolver affiliateShortLinkResolver,
            AllegroUrlNormalizer allegroUrlNormalizer,
            AliExpressUrlNormalizer aliExpressUrlNormalizer,
            TemuUrlNormalizer temuUrlNormalizer,
            AmazonUrlNormalizer amazonUrlNormalizer
    ) {
        this.affiliateShortLinkResolver = affiliateShortLinkResolver;
        this.allegroUrlNormalizer = allegroUrlNormalizer;
        this.aliExpressUrlNormalizer = aliExpressUrlNormalizer;
        this.temuUrlNormalizer = temuUrlNormalizer;
        this.amazonUrlNormalizer = amazonUrlNormalizer;
    }

    public boolean hasExistingAffiliateTracking(String productUrl) {
        if (productUrl == null || productUrl.isBlank()) {
            return false;
        }
        String trimmedUrl = productUrl.trim();
        if (affiliateShortLinkResolver.isWhitelistedShortLink(trimmedUrl)) {
            return true;
        }
        return hasPlatformAffiliateParams(trimmedUrl);
    }

    private boolean hasPlatformAffiliateParams(String url) {
        if (allegroUrlNormalizer.isAllegroUrl(url)) {
            return hasQueryParam(url, "affiliation");
        }
        if (aliExpressUrlNormalizer.isAliExpressUrl(url)) {
            return hasAnyQueryParam(url, ALIEXPRESS_AFFILIATE_PARAMS);
        }
        if (temuUrlNormalizer.isTemuUrl(url)) {
            return hasQueryParam(url, "referShareId") || hasQueryParam(url, "_p_rfs");
        }
        if (amazonUrlNormalizer.isAmazonUrl(url)) {
            if (isAmazonShortLink(url)) {
                return true;
            }
            return hasQueryParam(url, "tag");
        }
        return false;
    }

    private boolean isAmazonShortLink(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return lowerHost.equals("amzn.to") || lowerHost.endsWith(".amzn.to");
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean hasAnyQueryParam(String url, Set<String> paramNames) {
        for (String paramName : paramNames) {
            if (hasQueryParam(url, paramName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasQueryParam(String url, String paramName) {
        Map<String, String> params = queryParams(url);
        return params.containsKey(normalizeParamName(paramName));
    }

    private String normalizeParamName(String paramName) {
        return paramName.toLowerCase(Locale.ROOT);
    }

    private Map<String, String> queryParams(String url) {
        try {
            URI uri = URI.create(url.trim());
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return Map.of();
            }
            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            for (String pair : query.split("&")) {
                if (pair.isBlank()) {
                    continue;
                }
                int separatorIndex = pair.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String rawKey = pair.substring(0, separatorIndex);
                String key = decodeQueryComponent(rawKey).toLowerCase(Locale.ROOT);
                params.put(key, pair.substring(separatorIndex + 1));
            }
            return params;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String decodeQueryComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return value;
        }
    }
}
