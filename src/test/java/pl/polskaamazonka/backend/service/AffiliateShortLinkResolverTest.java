package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AffiliateShortLinkResolverTest {

    private static final String TEMU_SHORT_URL = "https://share.temu.com/abc123";
    private static final String TEMU_PRODUCT_URL = "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html";
    private static final String ALIEXPRESS_SHORT_URL = "https://s.click.aliexpress.com/e/_c2x0Gp5j";
    private static final String ALIEXPRESS_PRODUCT_URL = "https://pl.aliexpress.com/item/1005001234567890.html";

    private FakeShortLinkRedirectClient redirectClient;
    private AffiliateShortLinkResolver resolver;

    @BeforeEach
    void setUp() {
        redirectClient = new FakeShortLinkRedirectClient();
        resolver = new AffiliateShortLinkResolver(
                redirectClient,
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer()
        );
    }

    @Test
    void shareTemuExpandsToProductUrl() {
        redirectClient.expand(TEMU_SHORT_URL, TEMU_PRODUCT_URL);

        ShortLinkResolution resolution = resolver.resolve(TEMU_SHORT_URL);

        assertEquals(ShortLinkResolution.Status.SUCCESS, resolution.status());
        assertEquals(TEMU_PRODUCT_URL, resolution.expandedUrl());
    }

    @Test
    void aliExpressClickExpandsToProductUrl() {
        redirectClient.expand(ALIEXPRESS_SHORT_URL, ALIEXPRESS_PRODUCT_URL);

        ShortLinkResolution resolution = resolver.resolve(ALIEXPRESS_SHORT_URL);

        assertEquals(ShortLinkResolution.Status.SUCCESS, resolution.status());
        assertEquals(ALIEXPRESS_PRODUCT_URL, resolution.expandedUrl());
    }

    @Test
    void nonWhitelistedHostIsNotApplicable() {
        ShortLinkResolution resolution = resolver.resolve("https://bit.ly/abc123");

        assertEquals(ShortLinkResolution.Status.NOT_APPLICABLE, resolution.status());
        assertEquals(0, redirectClient.calls);
    }

    @Test
    void redirectToUnsupportedDomainFails() {
        redirectClient.expand(TEMU_SHORT_URL, "https://example.com/product/123");

        ShortLinkResolution resolution = resolver.resolve(TEMU_SHORT_URL);

        assertEquals(ShortLinkResolution.Status.FAILURE, resolution.status());
        assertEquals(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE, resolution.failureReason());
    }

    @Test
    void tooManyRedirectsFails() {
        redirectClient.fail(ALIEXPRESS_SHORT_URL, HttpShortLinkRedirectClient.TOO_MANY_REDIRECTS_MESSAGE);

        ShortLinkResolution resolution = resolver.resolve(ALIEXPRESS_SHORT_URL);

        assertEquals(ShortLinkResolution.Status.FAILURE, resolution.status());
        assertEquals(HttpShortLinkRedirectClient.TOO_MANY_REDIRECTS_MESSAGE, resolution.failureReason());
    }

    @Test
    void networkErrorFailsWithoutRetry() {
        redirectClient.fail(TEMU_SHORT_URL, HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);

        ShortLinkResolution resolution = resolver.resolve(TEMU_SHORT_URL);

        assertEquals(ShortLinkResolution.Status.FAILURE, resolution.status());
        assertEquals(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE, resolution.failureReason());
        assertEquals(1, redirectClient.calls);
    }

    @Test
    void optionalHostsAreWhitelisted() {
        assertTrue(resolver.isWhitelistedShortLink("https://temu.to/abc123"));
        assertTrue(resolver.isWhitelistedShortLink("https://star.aliexpress.com/share/abc123"));
    }

    private static class FakeShortLinkRedirectClient implements ShortLinkRedirectClient {
        private final Map<String, String> expansions = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();
        private int calls;

        void expand(String originalUrl, String expandedUrl) {
            expansions.put(originalUrl, expandedUrl);
        }

        void fail(String originalUrl, String reason) {
            failures.put(originalUrl, reason);
        }

        @Override
        public String expand(String url) {
            calls++;
            if (failures.containsKey(url)) {
                throw new ShortLinkExpansionException(failures.get(url));
            }
            return expansions.get(url);
        }
    }
}
