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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProductLinkUrlSupportTest {

    private ProductLinkUrlSupport support;

    @BeforeEach
    void setUp() {
        support = new ProductLinkUrlSupport(
                new QuickProductLinkValidator(
                        new AllegroUrlNormalizer(),
                        new AliExpressUrlNormalizer(),
                        new TemuUrlNormalizer(),
                        new AmazonUrlNormalizer(),
                        new AffiliateShortLinkResolver(
                                url -> "https://www.aliexpress.com/item/1005001234567890.html",
                                new AliExpressUrlNormalizer(),
                                new TemuUrlNormalizer()
                        )
                ),
                new AllegroUrlNormalizer(),
                new TemuUrlNormalizer(),
                new AmazonUrlNormalizer()
        );
    }

    @Test
    void storedUrlPreservesOriginalWithQueryParams() {
        String original = "https://allegro.pl/oferta/produkt-123456789?utm_source=abc&ref=xyz";
        QuickProductLinkValidationResult validation = support.validateProductUrl(original);

        assertEquals(original, support.storedUrl(validation));
        assertEquals("https://allegro.pl/oferta/produkt-123456789", support.verificationUrl(validation));
        assertNotEquals(support.storedUrl(validation), support.verificationUrl(validation));
    }

    @Test
    void storedUrlPreservesShortLinkWhileVerificationUsesExpandedUrl() {
        String shortLink = "https://s.click.aliexpress.com/e/_abc123";
        QuickProductLinkValidationResult validation = support.validateProductUrl(shortLink);

        assertEquals(shortLink, support.storedUrl(validation));
        assertEquals("https://www.aliexpress.com/item/1005001234567890.html", support.verificationUrl(validation));
    }

    @Test
    void storedUrlNeverUsesNormalizedUrlFromValidationRecord() {
        String original = "https://www.aliexpress.com/item/1005001234567890.html?spm=a2g0o";
        QuickProductLinkValidationResult manualValidation = QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALIEXPRESS,
                original,
                "https://www.aliexpress.com/item/1005001234567890.html",
                "1005001234567890"
        );

        assertEquals(original, support.storedUrl(manualValidation));
        assertEquals(
                "https://www.aliexpress.com/item/1005001234567890.html",
                support.verificationUrl(manualValidation)
        );
    }

    @Test
    void playwrightFinalUrlWouldNotBeStored() {
        String stored = "https://s.click.aliexpress.com/e/_abc123";
        String playwrightFinalUrl = "https://www.aliexpress.com/item/1005001234567890.html?aff_fsk=playwright";

        QuickProductLinkValidationResult validation = QuickProductLinkValidationResult.valid(
                SupportedProductPlatform.ALIEXPRESS,
                stored,
                playwrightFinalUrl,
                "1005001234567890"
        );

        assertEquals(stored, support.storedUrl(validation));
        assertEquals(playwrightFinalUrl, support.verificationUrl(validation));
        assertEquals(stored, validation.originalUrl());
        assertEquals(QuickProductLinkValidationStatus.VALID_PRODUCT_URL, validation.status());
    }
}
