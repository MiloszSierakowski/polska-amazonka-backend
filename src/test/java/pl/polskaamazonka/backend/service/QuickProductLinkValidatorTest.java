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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuickProductLinkValidatorTest {

    private QuickProductLinkValidator validator;

    @BeforeEach
    void setUp() {
        validator = new QuickProductLinkValidator(
                new AllegroUrlNormalizer(),
                new AliExpressUrlNormalizer(),
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer()
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
}
