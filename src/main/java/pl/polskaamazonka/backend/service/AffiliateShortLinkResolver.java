package pl.polskaamazonka.backend.service;

import org.springframework.stereotype.Component;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.net.URI;
import java.util.Set;

@Component
public class AffiliateShortLinkResolver {

    private static final Set<String> ALIEXPRESS_SHORT_HOSTS = Set.of(
            "s.click.aliexpress.com",
            "star.aliexpress.com"
    );
    private static final Set<String> TEMU_SHORT_HOSTS = Set.of(
            "share.temu.com",
            "temu.to"
    );

    private final ShortLinkRedirectClient shortLinkRedirectClient;
    private final AliExpressUrlNormalizer aliExpressUrlNormalizer;
    private final TemuUrlNormalizer temuUrlNormalizer;

    public AffiliateShortLinkResolver(
            ShortLinkRedirectClient shortLinkRedirectClient,
            AliExpressUrlNormalizer aliExpressUrlNormalizer,
            TemuUrlNormalizer temuUrlNormalizer
    ) {
        this.shortLinkRedirectClient = shortLinkRedirectClient;
        this.aliExpressUrlNormalizer = aliExpressUrlNormalizer;
        this.temuUrlNormalizer = temuUrlNormalizer;
    }

    public ShortLinkResolution resolve(String url) {
        if (url == null || url.isBlank()) {
            return ShortLinkResolution.notApplicable();
        }

        String shortHost = normalizedHost(url);
        if (!isWhitelistedShortHost(shortHost)) {
            return ShortLinkResolution.notApplicable();
        }

        try {
            String expandedUrl = shortLinkRedirectClient.expand(url.trim());
            validateExpandedUrl(shortHost, expandedUrl);
            return ShortLinkResolution.success(expandedUrl);
        } catch (ShortLinkExpansionException exception) {
            return ShortLinkResolution.failure(exception.getMessage());
        }
    }

    public boolean isWhitelistedShortLink(String url) {
        return isWhitelistedShortHost(normalizedHost(url));
    }

    private void validateExpandedUrl(String shortHost, String expandedUrl) {
        if (isWhitelistedShortLink(expandedUrl)) {
            throw new ShortLinkExpansionException(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);
        }
        if (ALIEXPRESS_SHORT_HOSTS.contains(shortHost)) {
            if (aliExpressUrlNormalizer.isAliExpressUrl(expandedUrl)
                    && aliExpressUrlNormalizer.extractItemId(expandedUrl) != null) {
                return;
            }
            throw new ShortLinkExpansionException(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);
        }
        if (TEMU_SHORT_HOSTS.contains(shortHost)) {
            if (temuUrlNormalizer.isTemuUrl(expandedUrl)
                    && temuUrlNormalizer.extractGoodsId(expandedUrl) != null) {
                return;
            }
            throw new ShortLinkExpansionException(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);
        }
        throw new ShortLinkExpansionException(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);
    }

    private boolean isWhitelistedShortHost(String host) {
        return host != null && (ALIEXPRESS_SHORT_HOSTS.contains(host) || TEMU_SHORT_HOSTS.contains(host));
    }

    private String normalizedHost(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return HttpShortLinkRedirectClient.normalizeHost(host);
        } catch (Exception exception) {
            return null;
        }
    }
}
