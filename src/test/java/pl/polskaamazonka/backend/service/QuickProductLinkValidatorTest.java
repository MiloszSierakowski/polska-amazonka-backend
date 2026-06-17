package pl.polskaamazonka.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationResult;
import pl.polskaamazonka.backend.dto.QuickProductLinkValidationStatus;
import pl.polskaamazonka.backend.dto.SupportedProductPlatform;
import pl.polskaamazonka.backend.service.scraper.AllegroUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AliExpressUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.AmazonUrlNormalizer;
import pl.polskaamazonka.backend.service.scraper.TemuUrlNormalizer;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuickProductLinkValidatorTest {

    private static final String TEMU_SHORT_URL = "https://share.temu.com/abc123";
    private static final String TEMU_PRODUCT_URL = "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html";
    private static final String ALIEXPRESS_SHORT_URL = "https://s.click.aliexpress.com/e/_c2x0Gp5j";
    private static final String ALIEXPRESS_PRODUCT_URL = "https://pl.aliexpress.com/item/1005001234567890.html";

    private FakeShortLinkRedirectClient shortLinkRedirectClient;
    private QuickProductLinkValidator validator;

    @BeforeEach
    void setUp() {
        shortLinkRedirectClient = new FakeShortLinkRedirectClient();
        AffiliateShortLinkResolver affiliateShortLinkResolver = new AffiliateShortLinkResolver(
                shortLinkRedirectClient,
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer()
        );
        validator = new QuickProductLinkValidator(
                new AllegroUrlNormalizer(),
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer(),
                affiliateShortLinkResolver
        );
    }

    @Test
    void allegro_validOfferUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://allegro.pl/oferta/nazwa-produktu-123456789"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALLEGRO, result.platform());
        assertEquals("123456789", result.externalProductId());
        assertEquals("https://allegro.pl/oferta/nazwa-produktu-123456789", result.normalizedUrl());
    }

    @Test
    void allegro_validOfferUrlWithTrackingQuery() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://allegro.pl/oferta/nazwa-produktu-123456789?utm_source=abc"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALLEGRO, result.platform());
        assertEquals("123456789", result.externalProductId());
        assertEquals("https://allegro.pl/oferta/nazwa-produktu-123456789", result.normalizedUrl());
    }

    @Test
    void allegro_listingIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://allegro.pl/listing?string=telefon"
        );

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALLEGRO, result.platform());
        assertNotNull(result.reason());
    }

    @Test
    void allegro_homepageIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("https://allegro.pl");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALLEGRO, result.platform());
    }

    @Test
    void allegro_categoryIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("https://allegro.pl/kategoria/telefony");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALLEGRO, result.platform());
    }

    @Test
    void aliExpress_validItemUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.aliexpress.com/item/1005001234567890.html"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
        assertEquals("1005001234567890", result.externalProductId());
        assertEquals("https://www.aliexpress.com/item/1005001234567890.html", result.normalizedUrl());
    }

    @Test
    void aliExpress_validItemUrlWithQuery() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://pl.aliexpress.com/item/1005001234567890.html?spm=a2g0o"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
        assertEquals("1005001234567890", result.externalProductId());
    }

    @Test
    void aliExpress_mobileItemUrlIsValid() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://m.aliexpress.com/item/1005001234567890.html"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
    }

    @Test
    void aliExpress_searchIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.aliexpress.com/w/wholesale-phone.html"
        );

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
    }

    @Test
    void aliExpress_homepageIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("https://www.aliexpress.com");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
    }

    @Test
    void temu_validProductPathUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.temu.com/pl/nazwa-produktu-g-601099999999999.html"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
        assertEquals("601099999999999", result.externalProductId());
    }

    @Test
    void temu_validGoodsIdQueryUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.temu.com/search_result.html?goods_id=601099999999999"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
        assertEquals("601099999999999", result.externalProductId());
    }

    @Test
    void temu_searchWithoutGoodsIdIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.temu.com/search_result.html"
        );

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
    }

    @Test
    void temu_homepageIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("https://www.temu.com");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
    }

    @Test
    void amazon_validDpUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.amazon.pl/dp/B0ABCDEFGH"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.AMAZON, result.platform());
        assertEquals("B0ABCDEFGH", result.externalProductId());
        assertEquals("https://www.amazon.pl/dp/B0ABCDEFGH", result.normalizedUrl());
    }

    @Test
    void amazon_validGpProductUrl() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.amazon.de/gp/product/B0ABCDEFGH"
        );

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.AMAZON, result.platform());
        assertEquals("B0ABCDEFGH", result.externalProductId());
        assertEquals("https://www.amazon.de/dp/B0ABCDEFGH", result.normalizedUrl());
    }

    @Test
    void amazon_searchIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate(
                "https://www.amazon.pl/s?k=telefon"
        );

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.AMAZON, result.platform());
    }

    @Test
    void amazon_homepageIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("https://www.amazon.pl");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.AMAZON, result.platform());
    }

    @Test
    void amazon_shortLinkIsInvalidWithoutExpansion() {
        QuickProductLinkValidationResult result = validator.validate("https://amzn.to/abc123");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.AMAZON, result.platform());
        assertNotNull(result.reason());
    }

    @Test
    void unsupportedPlatform_ebay() {
        QuickProductLinkValidationResult result = validator.validate("https://www.ebay.com/itm/123");

        assertEquals(QuickProductLinkValidationStatus.UNSUPPORTED_PLATFORM, result.status());
        assertEquals(SupportedProductPlatform.UNKNOWN, result.platform());
    }

    @Test
    void unsupportedPlatform_genericShop() {
        QuickProductLinkValidationResult result = validator.validate("https://mediamarkt.pl/produkt");

        assertEquals(QuickProductLinkValidationStatus.UNSUPPORTED_PLATFORM, result.status());
    }

    @Test
    void emptyStringIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertNull(result.normalizedUrl());
        assertNotNull(result.reason());
    }

    @Test
    void nonUrlTextIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("abc");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertNotNull(result.reason());
    }

    @Test
    void notAUrlIsInvalid() {
        QuickProductLinkValidationResult result = validator.validate("not-a-url");

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
    }

    @Test
    void temuShortLinkAfterExpansionIsValid() {
        shortLinkRedirectClient.expand(TEMU_SHORT_URL, TEMU_PRODUCT_URL);

        QuickProductLinkValidationResult result = validator.validate(TEMU_SHORT_URL);

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
        assertEquals(TEMU_SHORT_URL, result.originalUrl());
        assertEquals(TEMU_PRODUCT_URL, result.normalizedUrl());
        assertEquals("601099999999999", result.externalProductId());
    }

    @Test
    void aliExpressShortLinkAfterExpansionIsValid() {
        shortLinkRedirectClient.expand(ALIEXPRESS_SHORT_URL, ALIEXPRESS_PRODUCT_URL);

        QuickProductLinkValidationResult result = validator.validate(ALIEXPRESS_SHORT_URL);

        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.ALIEXPRESS, result.platform());
        assertEquals(ALIEXPRESS_SHORT_URL, result.originalUrl());
        assertEquals(ALIEXPRESS_PRODUCT_URL, result.normalizedUrl());
        assertEquals("1005001234567890", result.externalProductId());
    }

    @Test
    void shortLinkExpansionFailureIsInvalidWithReadableReason() {
        shortLinkRedirectClient.fail(TEMU_SHORT_URL, HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);

        QuickProductLinkValidationResult result = validator.validate(TEMU_SHORT_URL);

        assertEquals(QuickProductLinkValidationStatus.INVALID_PRODUCT_URL, result.status());
        assertEquals(SupportedProductPlatform.TEMU, result.platform());
        assertEquals(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE, result.reason());
    }

    private static class FakeShortLinkRedirectClient implements ShortLinkRedirectClient {
        private final Map<String, String> expansions = new HashMap<>();
        private final Map<String, String> failures = new HashMap<>();

        void expand(String originalUrl, String expandedUrl) {
            expansions.put(originalUrl, expandedUrl);
        }

        void fail(String originalUrl, String reason) {
            failures.put(originalUrl, reason);
        }

        @Override
        public String expand(String url) {
            if (failures.containsKey(url)) {
                throw new ShortLinkExpansionException(failures.get(url));
            }
            String expandedUrl = expansions.get(url);
            if (expandedUrl == null) {
                throw new ShortLinkExpansionException(HttpShortLinkRedirectClient.EXPANSION_FAILED_MESSAGE);
            }
            return expandedUrl;
        }
    }
}
